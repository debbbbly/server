package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.TopicStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.topic.repository.TopicSimilarityRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.category.repository.CategoryCachedRepository
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
    private val categoryRepository: CategoryCachedRepository,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher,
    private val topicSimilarityRepository: TopicSimilarityRepository
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

//        if (expiredUsers.isNotEmpty()) {
//            logger.info("Dropping {} users from queue who exceeded 15-minute timeout", expiredUsers.size)
//            expiredUsers.forEach { user ->
//                logger.info("  -> Dropping user {} (in queue since {})", user.userId, user.joinedAt)
//            }
//        }

        val sortedWaitingUsers = waitingUsers.sortedBy { it.joinedAt }
        val matchedUsers = mutableSetOf<String>()

//        logger.info("=== MATCHING CYCLE START ===")
//        logger.info("Queue size: {}", sortedWaitingUsers.size)

        if (sortedWaitingUsers.isEmpty()) {
//            logger.info("=== MATCHING CYCLE END (empty queue) ===")
            return
        }

        sortedWaitingUsers.forEach { user ->
            logger.info(
                "Queue entry: userId={}, claims={}, skipped={}, joinedAt={}, ignores={}",
                user.userId, user.claimIdToStance.size, user.skipClaimIds.size, user.joinedAt, user.ignores
            )
            logger.debug("  -> Claims: {}", user.claimIdToStance)
            logger.debug("  -> Skip list: {}", user.skipClaimIds)
        }

//        logger.debug("Phase 1: Matching users by common claims with opposite stances")
        for (i in 0 until sortedWaitingUsers.size) {
            val userA = sortedWaitingUsers[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until sortedWaitingUsers.size) {
                val userB = sortedWaitingUsers[j]
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

        // Phase 2: Similar topic matching (NEW)
        val remainingUsers = sortedWaitingUsers.filter { it.userId !in matchedUsers }
//        logger.debug("Phase 1 complete: {} users matched, {} remaining", matchedUsers.size, remainingUsers.size)
        if (remainingUsers.size >= 2) {
//            logger.debug("Phase 2: Matching users by similar topics with opposite stances")
            matchWithSimilarTopics(remainingUsers, matchedUsers)
        }

        // Phase 3: Individual stances with assignment (was Phase 2)
        val stillRemainingUsers = sortedWaitingUsers.filter { it.userId !in matchedUsers }
//        logger.debug(
//            "Phase 2 complete: {} users matched total, {} remaining",
//            matchedUsers.size,
//            stillRemainingUsers.size
//        )
        if (stillRemainingUsers.size >= 2) {
//            logger.debug("Phase 3: Matching users by individual stances with assignment")
            matchWithUserStances(stillRemainingUsers, matchedUsers)
        }

        // Phase 4: Top claims fallback (was Phase 3)
        val finallyRemainingUsers = sortedWaitingUsers.filter { it.userId !in matchedUsers }
        if (finallyRemainingUsers.size >= 2) {
//            logger.debug("Phase 4: Matching users by top claims with random stances")
            matchWithTopClaims(finallyRemainingUsers, matchedUsers)
        }

        val finalRemainingUsers = sortedWaitingUsers.filter { it.userId !in matchedUsers }

//        logger.info("=== MATCHING CYCLE COMPLETE ===")
//        logger.info("Total users matched: {}", matchedUsers.size)
//        logger.info("Users remaining in queue: {}", finalRemainingUsers.size)

        if (finalRemainingUsers.isNotEmpty()) {
//            logger.info("Re-queuing {} unmatched users:", finalRemainingUsers.size)
            finalRemainingUsers.forEach { user ->
//                logger.info("  -> User {}: {} claims, {} skipped", user.userId, user.claimIdToStance.size, user.skipClaimIds.size)
            }
        }

//        logger.debug("Clearing queue and re-adding {} remaining users", finalRemainingUsers.size)
        matchQueueRepository.removeAll()
        if (finalRemainingUsers.isNotEmpty()) {
            finalRemainingUsers.forEach { matchQueueRepository.save(it) }
        }

        val currentMatches = matchRepository.findAll()
//        logger.info("Current active matches: {}", currentMatches.size)
        currentMatches.forEach { match ->
//            logger.debug(
//                "  -> Match {}: {} vs {}, claim: {}, status: {}",
//                match.matchId,
//                match.opponents[0].userId,
//                match.opponents[1].userId,
//                match.claim.title,
//                match.status
//            )
        }
    }

    private fun cleanupExpiredMatches() {
        val now = Instant.now(clock)
        val expirationThreshold = now.minusSeconds(settings.getMatchTtl() - 1)

        val activeCategoryIds = categoryRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val expiredMatches = matchRepository.findAll()
            .filter { it.updatedAt.isBefore(expirationThreshold) && it.status == MatchStatus.PENDING }

        if (expiredMatches.isNotEmpty()) {
            logger.info("Found {} expired matches to clean up", expiredMatches.size)

            expiredMatches.forEach { match ->

                // Re-queue only users who accepted the match
                match.opponents
                    .filter { it.status == MatchOpponentStatus.ACCEPTED }
                    .forEach { opponent ->
                        val user = userRepository.getById(opponent.userId)
                        val claimIdToStance = userClaimRepository.findByUserId(user.userId)
                            .filter { it.claim.categoryId in activeCategoryIds }
                            .associate { it.claim.claimId to it.stance }

                            val matchRequest = MatchRequest(
                                userId = user.userId,
                                claimIdToStance = claimIdToStance,
                                skipClaimIds = emptySet(),
                                joinedAt = Instant.now(clock),
                                ignores = 0
                            )
                            matchQueueRepository.save(matchRequest)
                            logger.info("Re-queued user {} who accepted expired match {}", opponent.userId, match.matchId)
                    }

                matchRepository.delete(match.matchId)

                matchNotificationService.notifyMatchTimeout(match)
            }
        }
    }

    private fun matchWithTopClaims(remainingUsers: List<MatchRequest>, matchedUsers: MutableSet<String>) {
        val activeCategoryIds = categoryRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val top10Claims = claimRepository.findAll()
            .filter { it.categoryId in activeCategoryIds }
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

    private fun matchWithSimilarTopics(remainingUsers: List<MatchRequest>, matchedUsers: MutableSet<String>) {
        val SIMILARITY_THRESHOLD = 0.65

        val availableUsers = remainingUsers.filter { it.userId !in matchedUsers }.toMutableList()

        // Pre-fetch all claims for all users to avoid N+1 queries
        val userClaims = availableUsers.associate { user ->
            user.userId to user.claimIdToStance.keys
                .map { claimId -> claimRepository.getById(claimId) }
                .filter { it.topicId != null } // Only claims with topics
        }

        while (availableUsers.size >= 2) {
            val userA = availableUsers.removeFirstOrNull() ?: break
            val userB = availableUsers.firstOrNull() ?: break

            val claimsA = userClaims[userA.userId] ?: emptyList()
            val claimsB = userClaims[userB.userId] ?: emptyList()

            if (claimsA.isEmpty() || claimsB.isEmpty()) {
                continue // Skip if either user has no claims with topics
            }

            // Find best matching claim pair based on topic similarity
            var bestMatch: Triple<String, String, Double>? = null // (claimIdA, claimIdB, similarity)

            for (claimA in claimsA) {
                if (claimA.claimId in userA.skipClaimIds) continue
                if (claimA.topicStance == TopicStance.NEUTRAL) continue

                // Get similar topics for claimA's topic
                val similarTopics = topicSimilarityRepository.findSimilarTopics(claimA.topicId!!)
                    .filter { it.similarity >= SIMILARITY_THRESHOLD }
                    .associate { it.topicId2 to it.similarity }

                for (claimB in claimsB) {
                    if (claimB.claimId in userB.skipClaimIds) continue
                    if (claimB.topicStance == TopicStance.NEUTRAL) continue

                    // Check if topics are similar
                    val similarity = similarTopics[claimB.topicId] ?: continue

                    // Calculate effective topic stances
                    val topicStanceA = inferTopicStance(
                        userA.claimIdToStance[claimA.claimId],
                        claimA.topicStance
                    ) ?: continue

                    val topicStanceB = inferTopicStance(
                        userB.claimIdToStance[claimB.claimId],
                        claimB.topicStance
                    ) ?: continue

                    // Check if topic stances are opposite
                    if (areOppositeTopicStances(topicStanceA, topicStanceB)) {
                        if (bestMatch == null || similarity > bestMatch.third) {
                            bestMatch = Triple(claimA.claimId, claimB.claimId, similarity)
                        }
                    }
                }
            }

            if (bestMatch != null) {
                // Match found - select one of the claims
                // Prioritize userA's claim (could also use selectClaimWithPriority)
                val selectedClaimId = bestMatch.first

                matchedUsers.add(userA.userId)
                matchedUsers.add(userB.userId)
                availableUsers.remove(userB)

                createAndStoreMatch(userA, userB, selectedClaimId, MatchReason.SIMILAR_TOPIC)

                logger.info(
                    "Topic-similarity matched users {} and {} on claim {} (similarity: {})",
                    userA.userId, userB.userId, selectedClaimId, String.format("%.2f", bestMatch.third)
                )
            } else {
                // No match found, skip userA and try next pair
                continue
            }
        }
    }

    private fun inferTopicStance(claimStance: ClaimStance?, topicStance: TopicStance?): ClaimStance? {
        if (claimStance == null || topicStance == null) return null
        if (topicStance == TopicStance.NEUTRAL) return null

        return when {
            claimStance == ClaimStance.EITHER -> ClaimStance.EITHER

            topicStance == TopicStance.FOR && claimStance == ClaimStance.FOR -> ClaimStance.FOR
            topicStance == TopicStance.FOR && claimStance == ClaimStance.AGAINST -> ClaimStance.AGAINST

            topicStance == TopicStance.AGAINST && claimStance == ClaimStance.FOR -> ClaimStance.AGAINST
            topicStance == TopicStance.AGAINST && claimStance == ClaimStance.AGAINST -> ClaimStance.FOR

            else -> null
        }
    }

    private fun areOppositeTopicStances(stanceA: ClaimStance, stanceB: ClaimStance): Boolean {
        if (stanceA == ClaimStance.EITHER || stanceB == ClaimStance.EITHER) return true
        return (stanceA == ClaimStance.FOR && stanceB == ClaimStance.AGAINST) ||
               (stanceA == ClaimStance.AGAINST && stanceB == ClaimStance.FOR)
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

        logger.info(
            "Creating match: reason={}, userA={} (stance={}), userB={} (stance={}), claim={} ('{}')",
            reason, userA.userId, stanceA, userB.userId, stanceB, claimId, claim.title
        )

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

        if (finalA != stanceA || finalB != stanceB) {
            logger.info("Stances adjusted: userA {} -> {}, userB {} -> {}", stanceA, finalA, stanceB, finalB)
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
        logger.info(
            "Match created and saved: matchId={}, {} ({}/{}) vs {} ({}/{}), ttl={}s",
            matchId,
            userAEntity.username, userA.userId, finalA,
            userBEntity.username, userB.userId, finalB,
            settings.getMatchTtl()
        )
        eventPublisher.publishEvent(MatchFoundEvent(match))
    }

    private fun ClaimStance.opposite(): ClaimStance = when (this) {
        ClaimStance.FOR -> ClaimStance.AGAINST
        ClaimStance.AGAINST -> ClaimStance.FOR
        ClaimStance.EITHER -> error("No opposite for EITHER")
    }
}
