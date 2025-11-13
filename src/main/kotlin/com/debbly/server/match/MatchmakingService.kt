package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
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
class MatchmakingService(
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimCachedRepository,
    private val matchNotificationService: MatchNotificationService,
    private val settings: SettingsService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun runMatching() {
        cleanupExpiredMatches()

        val waitingUsers = matchQueueRepository.findAll().sortedBy { it.joinedAt }
        val matchedUsers = mutableSetOf<String>()

        waitingUsers.forEach { user ->
            logger.debug(
                "User {} in queue: {} claims, {} skipped, waiting since {}",
                user.userId, user.claimIdToStance.size, user.skipClaimIds.size, user.joinedAt
            )
        }

        logger.debug("Phase 1: Matching users by common claims with opposite stances")
        for (i in 0 until waitingUsers.size) {
            val userA = waitingUsers[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until waitingUsers.size) {
                val userB = waitingUsers[j]
                if (userB.userId in matchedUsers) continue

                val matchingClaimIds = findMatchingClaimsWithOppositeStances(userA, userB)

                if (matchingClaimIds.isNotEmpty()) {
                    val selectedClaimId = selectClaimWithPriority(matchingClaimIds, userA.claimIdToStance.keys.toList())

                    matchedUsers.add(userA.userId)
                    matchedUsers.add(userB.userId)

                    createAndStoreMatch(userA, userB, selectedClaimId, MatchReason.COMMON_STANCE_OPPOSITE)

                    logger.debug(
                        "Matched users {} and {} on claim {} (selected from {} options with priority)",
                        userA.userId, userB.userId, selectedClaimId, matchingClaimIds.size
                    )
                    break
                }
            }
        }

        val remainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        logger.debug("Phase 1 complete: {} users matched, {} remaining", matchedUsers.size, remainingUsers.size)
        if (remainingUsers.size >= 2) {
            logger.debug("Phase 2: Matching users by individual stances with assignment")
            matchWithUserStances(remainingUsers, matchedUsers)
        }

        val stillRemainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        logger.debug(
            "Phase 2 complete: {} users matched total, {} remaining",
            matchedUsers.size,
            stillRemainingUsers.size
        )
        if (stillRemainingUsers.size >= 2) {
            logger.debug("Phase 3: Matching users by top claims with random stances")
            matchWithTopClaims(stillRemainingUsers, matchedUsers)
        }

        val finalRemainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        matchQueueRepository.removeAll()
        if (finalRemainingUsers.isNotEmpty()) {
            finalRemainingUsers.forEach { matchQueueRepository.save(it) }
        }
    }

    private fun cleanupExpiredMatches() {
        val now = Instant.now(clock)
        val expirationThreshold = now.minusSeconds(settings.getMatchTtl() - 1)

        val expiredMatches = matchRepository.findAll()
            .filter { it.updatedAt.isBefore(expirationThreshold) && it.status == MatchStatus.PENDING }

        if (expiredMatches.isNotEmpty()) {
            logger.info("Found {} expired matches to clean up", expiredMatches.size)

            expiredMatches.forEach { match ->
                matchNotificationService.notifyMatchTimeout(match)

                try {
                    match.opponents.forEach { _ ->
                    }
                } catch (e: Exception) {
                    logger.error("Error cleaning up expired match ${match.matchId}", e)
                }

                matchRepository.delete(match.matchId)
            }
        }
    }

    private fun matchWithTopClaims(remainingUsers: List<MatchRequest>, matchedUsers: MutableSet<String>) {
        val top10Claims = claimRepository.findAll()
            .filter { it.category.active }
            .sortedByDescending { it.scoreTotal ?: 0.0 }
            .take(10)

        if (top10Claims.isEmpty()) {
            logger.warn("No active claims available for fallback matching")
            return
        }

        val availableUsers = remainingUsers.filter { it.userId !in matchedUsers }.toMutableList()

        while (availableUsers.size >= 2) {
            val userA = availableUsers.removeFirstOrNull() ?: break
            val userB = availableUsers.removeFirstOrNull() ?: break

            val suitableClaims = top10Claims.filter { claim ->
                claim.claimId !in userA.skipClaimIds && claim.claimId !in userB.skipClaimIds
            }

            if (suitableClaims.isNotEmpty()) {
                val selectedClaim = suitableClaims.random()
                val stances = listOf(ClaimStance.FOR, ClaimStance.AGAINST).shuffled()
                val userAStance = stances[0]
                val userBStance = stances[1]

                val userAWithStance = userA.copy(
                    claimIdToStance = userA.claimIdToStance + (selectedClaim.claimId to userAStance)
                )
                val userBWithStance = userB.copy(
                    claimIdToStance = userB.claimIdToStance + (selectedClaim.claimId to userBStance)
                )

                matchedUsers.add(userA.userId)
                matchedUsers.add(userB.userId)

                createAndStoreMatch(
                    userAWithStance,
                    userBWithStance,
                    selectedClaim.claimId,
                    MatchReason.TOP_CLAIM_RANDOM
                )

                logger.debug(
                    "Fallback matched users {} ({}) and {} ({}) on top claim {} with score {}",
                    userA.userId, userAStance, userB.userId, userBStance,
                    selectedClaim.claimId, selectedClaim.scoreTotal
                )
            } else {
                availableUsers.add(0, userB)
                availableUsers.add(0, userA)
                break
            }
        }
    }

    private fun selectClaimWithPriority(availableClaims: List<String>, userClaimOrder: List<String>): String {
        if (availableClaims.size == 1) return availableClaims.first()

        val weights = availableClaims.map { claimId ->
            val position = userClaimOrder.indexOf(claimId)
            val weight = if (position >= 0) {
                1.0 / (position + 1.0)
            } else {
                0.1
            }
            claimId to weight
        }

        val totalWeight = weights.sumOf { it.second }
        val random = kotlin.random.Random.nextDouble() * totalWeight

        var currentWeight = 0.0
        for ((claimId, weight) in weights) {
            currentWeight += weight
            if (random <= currentWeight) {
                return claimId
            }
        }

        return availableClaims.last()
    }

    private fun matchWithUserStances(remainingUsers: List<MatchRequest>, matchedUsers: MutableSet<String>) {
        val availableUsers = remainingUsers.filter { it.userId !in matchedUsers }.toMutableList()

        while (availableUsers.size >= 2) {
            val userA = availableUsers.removeFirstOrNull() ?: break
            val userB = availableUsers.removeFirstOrNull() ?: break

            val allUserStances = (userA.claimIdToStance.keys.toList() + userB.claimIdToStance.keys.toList()).distinct()

            val suitableClaims = allUserStances.filter { claimId ->
                claimId !in userA.skipClaimIds && claimId !in userB.skipClaimIds
            }

            if (suitableClaims.isNotEmpty()) {
                val selectedClaimId = selectClaimWithPriority(suitableClaims, allUserStances)

                val userAStance = userA.claimIdToStance[selectedClaimId]
                val userBStance = userB.claimIdToStance[selectedClaimId]

                val finalUserAStance = userAStance ?: userBStance?.opposite() ?: ClaimStance.FOR
                val finalUserBStance = userBStance ?: userAStance?.opposite() ?: ClaimStance.AGAINST

                val userAWithStance = userA.copy(
                    claimIdToStance = userA.claimIdToStance + (selectedClaimId to finalUserAStance)
                )
                val userBWithStance = userB.copy(
                    claimIdToStance = userB.claimIdToStance + (selectedClaimId to finalUserBStance)
                )

                matchedUsers.add(userA.userId)
                matchedUsers.add(userB.userId)

                createAndStoreMatch(userAWithStance, userBWithStance, selectedClaimId, MatchReason.USER_STANCE_ASSIGNED)

                logger.debug(
                    "User-stance matched users {} ({}) and {} ({}) on claim {}",
                    userA.userId, finalUserAStance, userB.userId, finalUserBStance, selectedClaimId
                )
            } else {
                availableUsers.add(0, userB)
                availableUsers.add(0, userA)
                break
            }
        }
    }

    private fun findMatchingClaimsWithOppositeStances(userA: MatchRequest, userB: MatchRequest): List<String> {
        return userA.claimIdToStance.keys.intersect(userB.claimIdToStance.keys)
            .filter { claimId ->
                val stanceA = userA.claimIdToStance[claimId]
                val stanceB = userB.claimIdToStance[claimId]
                areOpposite(stanceA, stanceB)
            }
            .filter { claimId ->
                claimId !in userA.skipClaimIds && claimId !in userB.skipClaimIds
            }
    }

    private fun areOpposite(claimStanceA: ClaimStance?, claimStanceB: ClaimStance?): Boolean {
        if (claimStanceA == null || claimStanceB == null) return false
        if (claimStanceA == ClaimStance.EITHER && (claimStanceB == ClaimStance.FOR || claimStanceB == ClaimStance.AGAINST)) return true
        if (claimStanceB == ClaimStance.EITHER && (claimStanceA == ClaimStance.FOR || claimStanceA == ClaimStance.AGAINST)) return true
        if (claimStanceA == ClaimStance.FOR && claimStanceB == ClaimStance.AGAINST) return true
        if (claimStanceA == ClaimStance.AGAINST && claimStanceB == ClaimStance.FOR) return true
        return false
    }

    private fun createAndStoreMatch(userA: MatchRequest, userB: MatchRequest, claimId: String, reason: MatchReason) {
        val matchId = idService.getId()
        val claim = claimRepository.getById(claimId)
        val userAEntity = userRepository.getById(userA.userId)
        val userBEntity = userRepository.getById(userB.userId)
        val now = Instant.now(clock)

        val stanceA = userA.claimIdToStance[claimId] ?: ClaimStance.EITHER
        val stanceB = userB.claimIdToStance[claimId] ?: ClaimStance.EITHER

        val (finalA, finalB) = when {
            stanceA != ClaimStance.EITHER && stanceB == ClaimStance.EITHER ->
                stanceA to stanceA.opposite()

            stanceA == ClaimStance.EITHER && stanceB != ClaimStance.EITHER ->
                stanceB.opposite() to stanceB

            stanceA == ClaimStance.EITHER && stanceB == ClaimStance.EITHER ->
                ClaimStance.FOR to ClaimStance.AGAINST

            else ->
                stanceA to stanceB
        }

        val match = Match(
            matchId = matchId,
            claim = Match.MatchClaim(claim.claimId, claim.title),
            status = MatchStatus.PENDING,
            opponents = listOf(
                Match.MatchOpponent(
                    userId = userAEntity.userId,
                    username = userAEntity.username,
                    avatarUrl = userAEntity.avatarUrl,
                    stance = finalA,
                    status = MatchOpponentStatus.PENDING,
                    userA.ignores
                ),
                Match.MatchOpponent(
                    userId = userBEntity.userId,
                    username = userBEntity.username,
                    avatarUrl = userBEntity.avatarUrl,
                    stance = finalB,
                    status = MatchOpponentStatus.PENDING,
                    userB.ignores
                )
            ),
            updatedAt = now,
            ttl = settings.getMatchTtl()
        )

        matchRepository.save(match)
        eventPublisher.publishEvent(MatchFoundEvent(match))
    }

    private fun ClaimStance.opposite(): ClaimStance = when (this) {
        ClaimStance.FOR -> ClaimStance.AGAINST
        ClaimStance.AGAINST -> ClaimStance.FOR
        ClaimStance.EITHER -> error("No opposite for EITHER")
    }
}
