package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.StanceToTopic
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.top.TopClaimsService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.match.event.MatchFoundEvent
import com.debbly.server.match.model.*
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.settings.SettingsService
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class MatchingJobService(
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserCachedRepository,
    private val userClaimRepository: UserClaimCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimCachedRepository,
    private val matchNotificationService: MatchNotificationService,
    private val settings: SettingsService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher,
    private val topClaimsService: TopClaimsService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val QUEUE_TIMEOUT_SECONDS = 15 * 60L // 15 minutes
    }

    fun runMatching() {
        cleanupExpiredMatches()

        val allUsers = matchQueueRepository.findAll()
        val now = Instant.now(clock)
        val queueTimeoutThreshold = now.minusSeconds(QUEUE_TIMEOUT_SECONDS)

        // Filter out users who have been in queue for more than 15 minutes
        val (expiredUsers, waitingUsers) = allUsers.partition { it.joinedAt.isBefore(queueTimeoutThreshold) }

        // Notify expired users
        if (expiredUsers.isNotEmpty()) {
            expiredUsers.forEach { matchQueueRepository.remove(it.userId) }
            logger.debug("Removed {} expired users from queue", expiredUsers.size)
        }

        val sortedWaitingUsers = waitingUsers.sortedBy { it.joinedAt }
        val matchedUsers = mutableSetOf<String>()

        if (sortedWaitingUsers.isEmpty()) {
            return
        }

        logger.debug("Starting matching cycle with {} users in queue", sortedWaitingUsers.size)

        // Phase 1: Claim Match (Strict)
        matchClaimPhase(sortedWaitingUsers, matchedUsers)

        // Phase 2: Topic Match (Flexible)
        val remainingUsersWithTopics =
            sortedWaitingUsers
                .filter { it.userId !in matchedUsers && it.hasTopics() }
        if (remainingUsersWithTopics.size >= 2) {
            matchTopicPhase(remainingUsersWithTopics, matchedUsers)
        }

        // Notify remaining users that they're still waiting
        val finalRemainingUsers = sortedWaitingUsers.filter { it.userId !in matchedUsers }
        if (finalRemainingUsers.isNotEmpty()) {
            matchNotificationService.notifyStillWaiting(finalRemainingUsers.map { it.userId })
        }

        // Clean up and re-queue remaining users
        matchQueueRepository.removeAll()
        if (finalRemainingUsers.isNotEmpty()) {
            finalRemainingUsers.forEach { matchQueueRepository.save(it) }
        }

        if (matchedUsers.isNotEmpty()) {
            logger.debug("Matching cycle complete: {} users matched, {} remaining in queue", matchedUsers.size, finalRemainingUsers.size)
        }
    }

    /**
     * Phase 1: Claim Match (Strict)
     * Find opponent with same claimId + opposite stance.
     * Users with only claims stay here (no topic fallback).
     */
    private fun matchClaimPhase(
        users: List<MatchRequest>,
        matchedUsers: MutableSet<String>,
    ) {
        for (i in users.indices) {
            val userA = users[i]
            if (userA.userId in matchedUsers) continue
            if (userA.claims.isEmpty()) continue

            for (j in i + 1 until users.size) {
                val userB = users[j]
                if (userB.userId in matchedUsers) continue
                if (userB.claims.isEmpty()) continue

                // Check skip lists
                if (userA.userId in userB.skipUserIds || userB.userId in userA.skipUserIds) continue

                // Find matching claim with opposite stances
                val matchingClaim = findClaimMatchWithOppositeStances(userA, userB)

                if (matchingClaim != null) {
                    matchedUsers.add(userA.userId)
                    matchedUsers.add(userB.userId)

                    createAndStoreMatch(
                        userA = userA,
                        userB = userB,
                        claimId = matchingClaim.claimId,
                        stanceA = matchingClaim.stanceA,
                        stanceB = matchingClaim.stanceB,
                        topicId = null,
                        reason = MatchReason.CLAIM_MATCH,
                    )

                    logger.debug(
                        "Phase 1 (Claim Match): Matched users {} and {} on claim {}",
                        userA.userId,
                        userB.userId,
                        matchingClaim.claimId,
                    )
                    break
                }
            }
        }
    }

    /**
     * Phase 2: Topic Match (Flexible)
     * For users with topics in their request.
     */
    private fun matchTopicPhase(
        users: List<MatchRequest>,
        matchedUsers: MutableSet<String>,
    ) {
        for (i in users.indices) {
            val userA = users[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until users.size) {
                val userB = users[j]
                if (userB.userId in matchedUsers) continue

                // Check skip lists
                if (userA.userId in userB.skipUserIds || userB.userId in userA.skipUserIds) continue

                // Find matching topic with opposite stances
                val matchResult = findTopicMatch(userA, userB)

                if (matchResult != null) {
                    matchedUsers.add(userA.userId)
                    matchedUsers.add(userB.userId)

                    createAndStoreMatch(
                        userA = userA,
                        userB = userB,
                        claimId = matchResult.claimId,
                        stanceA = matchResult.stanceA,
                        stanceB = matchResult.stanceB,
                        topicId = matchResult.topicId,
                        reason = matchResult.reason,
                    )

                    logger.debug(
                        "Phase 2 (Topic Match): Matched users {} and {} on claim {} via topic {} ({})",
                        userA.userId,
                        userB.userId,
                        matchResult.claimId,
                        matchResult.topicId,
                        matchResult.reason,
                    )
                    break
                }
            }
        }
    }

    private data class ClaimMatchResult(
        val claimId: String,
        val stanceA: ClaimStance,
        val stanceB: ClaimStance,
    )

    private data class TopicMatchResult(
        val topicId: String,
        val claimId: String,
        val stanceA: ClaimStance,
        val stanceB: ClaimStance,
        val reason: MatchReason,
    )

    /**
     * Find a claim where both users have opposite stances.
     */
    private fun findClaimMatchWithOppositeStances(
        userA: MatchRequest,
        userB: MatchRequest,
    ): ClaimMatchResult? {
        val claimsA = userA.claims.associateBy { it.claimId }
        val claimsB = userB.claims.associateBy { it.claimId }

        // Find common claims
        val commonClaimIds =
            claimsA.keys
                .intersect(claimsB.keys)
                .filter { claimId ->
                    claimId !in userA.skipClaimIds && claimId !in userB.skipClaimIds
                }

        for (claimId in commonClaimIds) {
            val stanceA = claimsA[claimId]!!.stance
            val stanceB = claimsB[claimId]!!.stance

            if (areOpposite(stanceA, stanceB)) {
                return ClaimMatchResult(claimId, stanceA, stanceB)
            }
        }

        return null
    }

    /**
     * Find a topic match between two users.
     * Sub-phase A: Find claim where both users have opposite stances in DB
     * Sub-phase B: Pick top claim, assign stances based on topic stance + claim's stanceToTopic
     */
    private fun findTopicMatch(
        userA: MatchRequest,
        userB: MatchRequest,
    ): TopicMatchResult? {
        val topicsA = userA.topics.associateBy { it.topicId }
        val topicsB = userB.topics.associateBy { it.topicId }

        // Find common topics with opposite stances
        val commonTopicIds = topicsA.keys.intersect(topicsB.keys)

        for (topicId in commonTopicIds) {
            val topicStanceA = topicsA[topicId]!!.stance
            val topicStanceB = topicsB[topicId]!!.stance

            // Users need opposite stances on the topic
            if (!areOpposite(topicStanceA, topicStanceB)) continue

            // Get top claims for this topic
            val topClaims = topClaimsService.getTopClaimsForTopic(topicId)
            if (topClaims.isEmpty()) continue

            // Sub-phase A: Check if both users have existing opposite stances on any claim
            val userADbStances =
                userClaimRepository
                    .findByUserId(userA.userId)
                    .filter { it.claim.topicId == topicId }
                    .associateBy { it.claim.claimId }
            val userBDbStances =
                userClaimRepository
                    .findByUserId(userB.userId)
                    .filter { it.claim.topicId == topicId }
                    .associateBy { it.claim.claimId }

            for (topClaim in topClaims) {
                if (topClaim.claimId in userA.skipClaimIds || topClaim.claimId in userB.skipClaimIds) continue

                val dbStanceA = userADbStances[topClaim.claimId]?.stance
                val dbStanceB = userBDbStances[topClaim.claimId]?.stance

                if (dbStanceA != null && dbStanceB != null && areOpposite(dbStanceA, dbStanceB)) {
                    return TopicMatchResult(
                        topicId = topicId,
                        claimId = topClaim.claimId,
                        stanceA = dbStanceA,
                        stanceB = dbStanceB,
                        reason = MatchReason.TOPIC_MATCH_EXISTING_STANCE,
                    )
                }
            }

            // Sub-phase B: Pick top claim, derive stances from topic stance + claim's stanceToTopic
            for (topClaim in topClaims) {
                if (topClaim.claimId in userA.skipClaimIds || topClaim.claimId in userB.skipClaimIds) continue

                val claim = claimRepository.findById(topClaim.claimId) ?: continue
                if (claim.stanceToTopic == StanceToTopic.NEUTRAL) continue

                val derivedStanceA = deriveClaimStance(topicStanceA, claim.stanceToTopic)
                val derivedStanceB = deriveClaimStance(topicStanceB, claim.stanceToTopic)

                if (derivedStanceA != null && derivedStanceB != null && areOpposite(derivedStanceA, derivedStanceB)) {
                    return TopicMatchResult(
                        topicId = topicId,
                        claimId = topClaim.claimId,
                        stanceA = derivedStanceA,
                        stanceB = derivedStanceB,
                        reason = MatchReason.TOPIC_MATCH_DERIVED_STANCE,
                    )
                }
            }
        }

        return null
    }

    /**
     * Derive claim stance from topic stance and claim's stanceToTopic.
     *
     * Stance derivation logic:
     * - User FOR topic + Claim FOR topic → User FOR claim
     * - User FOR topic + Claim AGAINST topic → User AGAINST claim
     * - User AGAINST topic + Claim FOR topic → User AGAINST claim
     * - User AGAINST topic + Claim AGAINST topic → User FOR claim
     */
    private fun deriveClaimStance(
        topicStance: ClaimStance,
        claimStanceToTopic: StanceToTopic,
    ): ClaimStance? =
        when {
            topicStance == ClaimStance.FOR && claimStanceToTopic == StanceToTopic.FOR -> ClaimStance.FOR
            topicStance == ClaimStance.FOR && claimStanceToTopic == StanceToTopic.AGAINST -> ClaimStance.AGAINST
            topicStance == ClaimStance.AGAINST && claimStanceToTopic == StanceToTopic.FOR -> ClaimStance.AGAINST
            topicStance == ClaimStance.AGAINST && claimStanceToTopic == StanceToTopic.AGAINST -> ClaimStance.FOR
            else -> null
        }

    private fun cleanupExpiredMatches() {
        val now = Instant.now(clock)
        val expirationThreshold = now.minusSeconds(settings.getMatchTtl() - 1)

        val expiredMatches =
            matchRepository
                .findAll()
                .filter { it.updatedAt.isBefore(expirationThreshold) && it.status == MatchStatus.PENDING }

        if (expiredMatches.isNotEmpty()) {
            logger.debug("Cleaning up {} expired matches", expiredMatches.size)

            expiredMatches.forEach { match ->
                // Re-queue only users who accepted the match
                match.opponents
                    .filter { it.status == MatchOpponentStatus.ACCEPTED }
                    .forEach { opponent ->
                        // Note: We can't rebuild the original request perfectly here,
                        // so we just remove them from the match and let them rejoin manually
                        logger.debug("User {} was in expired match, they need to rejoin", opponent.userId)
                    }

                matchRepository.delete(match.matchId)
                matchNotificationService.notifyMatchTimeout(match)
            }
        }
    }

    private fun areOpposite(
        stanceA: ClaimStance?,
        stanceB: ClaimStance?,
    ): Boolean {
        if (stanceA == null || stanceB == null) return false
        if (stanceA == ClaimStance.EITHER && (stanceB == ClaimStance.FOR || stanceB == ClaimStance.AGAINST)) return true
        if (stanceB == ClaimStance.EITHER && (stanceA == ClaimStance.FOR || stanceA == ClaimStance.AGAINST)) return true
        if (stanceA == ClaimStance.FOR && stanceB == ClaimStance.AGAINST) return true
        if (stanceA == ClaimStance.AGAINST && stanceB == ClaimStance.FOR) return true
        return false
    }

    private fun createAndStoreMatch(
        userA: MatchRequest,
        userB: MatchRequest,
        claimId: String,
        stanceA: ClaimStance,
        stanceB: ClaimStance,
        topicId: String?,
        reason: MatchReason,
    ) {
        val matchId = idService.getId()
        val claim = claimRepository.getById(claimId)
        val userAEntity = userRepository.getById(userA.userId)
        val userBEntity = userRepository.getById(userB.userId)
        val now = Instant.now(clock)

        // Resolve EITHER stances
        val (finalA, finalB) =
            when {
                stanceA != ClaimStance.EITHER && stanceB == ClaimStance.EITHER ->
                    stanceA to stanceA.opposite()

                stanceA == ClaimStance.EITHER && stanceB != ClaimStance.EITHER ->
                    stanceB.opposite() to stanceB

                stanceA == ClaimStance.EITHER && stanceB == ClaimStance.EITHER ->
                    ClaimStance.FOR to ClaimStance.AGAINST

                else ->
                    stanceA to stanceB
            }

        val match =
            Match(
                matchId = matchId,
                claim = Match.MatchClaim(claim.claimId, claim.title),
                topicId = topicId,
                matchReason = reason,
                status = MatchStatus.PENDING,
                opponents =
                    listOf(
                        Match.MatchOpponent(
                            userId = userAEntity.userId,
                            username = userAEntity.username,
                            avatarUrl = userAEntity.avatarUrl,
                            stance = finalA,
                            status = MatchOpponentStatus.PENDING,
                            userA.ignores,
                        ),
                        Match.MatchOpponent(
                            userId = userBEntity.userId,
                            username = userBEntity.username,
                            avatarUrl = userBEntity.avatarUrl,
                            stance = finalB,
                            status = MatchOpponentStatus.PENDING,
                            userB.ignores,
                        ),
                    ),
                updatedAt = now,
                ttl = settings.getMatchTtl(),
            )

        matchRepository.save(match)
        logger.info("Match created: {} vs {}, claim: '{}', reason: {}", userAEntity.username, userBEntity.username, claim.title, reason)
        eventPublisher.publishEvent(MatchFoundEvent(match))
    }

    private fun ClaimStance.opposite(): ClaimStance =
        when (this) {
            ClaimStance.FOR -> ClaimStance.AGAINST
            ClaimStance.AGAINST -> ClaimStance.FOR
            ClaimStance.EITHER -> error("No opposite for EITHER")
        }
}
