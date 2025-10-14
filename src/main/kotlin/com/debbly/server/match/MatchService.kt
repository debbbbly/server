package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.match.MatchService.MatchingStatus.*
import com.debbly.server.match.model.*
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.settings.SettingsService
import com.debbly.server.stage.StageService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class MatchService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimCachedRepository,
    private val categoryRepository: CategoryCachedRepository,
    private val userClaimService: UserClaimService,
    private val stageService: StageService,
    private val matchNotificationService: MatchNotificationService,
    private val settings: SettingsService,
    private val clock: Clock
) {
    companion object {
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun join(user: UserModel) {
        logger.info("User {} joined matchmaking queue", user.userId)
        matchQueueRepository.save(buildMatchRequest(user))
    }

    private fun buildMatchRequest(
        user: UserModel,
        withSkipClaimIds: Collection<String>? = null,
        withClaimIdToStance: Collection<Pair<String, ClaimStance>>? = null,
        ignores: Int = 0
    ): MatchRequest {
        val activeCategoryIds = categoryRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val userClaims = userClaimRepository.findByUserId(user.userId)
            .filter { it.claim.category.categoryId in activeCategoryIds }

        return MatchRequest(
            userId = user.userId,
            claimIdToStance = userClaims.associate { it.claim.claimId to it.stance }
                .plus(withClaimIdToStance.orEmpty()),
            skipClaimIds = withSkipClaimIds.orEmpty(),
            joinedAt = Instant.now(clock),
            ignores = ignores
        )
    }

    fun leave(user: UserModel) {
        logger.info("User {} left matchmaking queue", user.userId)
        matchQueueRepository.remove(userId = user.userId)
    }

    fun skip(match: Match, user: UserModel) {
        logger.info(
            "User {} skipped match {} on claim '{}'. Opponent(s): {}",
            user.userId, match.matchId, match.claim.title,
            match.opponents.filter { it.userId != user.userId }.map { it.userId }
        )

        matchNotificationService.notifyMatchCancelled(match, user.userId, "skip")

        matchRepository.delete(match.matchId)

        matchQueueRepository.save(
            buildMatchRequest(
                user,
                withSkipClaimIds = setOf(match.claim.claimId)
            )
        )
        match.opponents.filter { it.userId != user.userId }.forEach { otherUser ->
            matchQueueRepository.save(
                request = buildMatchRequest(
                    userRepository.getById(otherUser.userId),
                )
            )
        }

        logger.debug("Re-queued {} users after skip", match.opponents.size)
    }

    fun switch(match: Match, user: UserModel) {
        val userStance = match.opponents
            .firstOrNull() { it.userId == user.userId }
            ?.stance

        logger.info(
            "User {} switched stance to {} on match {} for claim '{}'. Opponent(s): {}",
            user.userId, userStance, match.matchId, match.claim.title,
            match.opponents.filter { it.userId != user.userId }.map { it.userId }
        )

        val withClaimIdToStance = userStance
            ?.let { match.claim.claimId to it }
            ?.also { (_, stance) ->
                userClaimService.updateStance(
                    userId = user.userId,
                    claimId = match.claim.claimId,
                    stance = stance
                )
            }
            ?.let { listOf(it) }
            .orEmpty()

        matchRepository.delete(match.matchId)
        matchNotificationService.notifyMatchCancelled(match, user.userId, "switch")

        // User actively switched stance, so preserve their ignore count (this is not an ignore)
        matchQueueRepository.save(
            buildMatchRequest(
                user,
                withClaimIdToStance = withClaimIdToStance,
            )
        )
        match.opponents.filter { it.userId != user.userId }.forEach { otherUser ->
            matchQueueRepository.save(
                request = buildMatchRequest(
                    userRepository.getById(otherUser.userId),
                )
            )
        }

        logger.debug("Re-queued {} users after switch", match.opponents.size)
    }

    fun accept(match: Match, user: UserModel) {
        logger.info(
            "User {} accepted match {} for claim '{}'",
            user.userId, match.matchId, match.claim.title
        )

        matchQueueRepository.remove(user.userId)

        val acceptedMatch = match.copy(
            opponents = match.opponents
                .map { opponent ->
                    if (opponent.userId == user.userId) opponent.copy(status = MatchOpponentStatus.ACCEPTED) else opponent
                },
            updatedAt = Instant.now(clock)
        )

        if (acceptedMatch.opponents.any { it.status != MatchOpponentStatus.ACCEPTED }) {
            logger.debug(
                "Match {} partially accepted. Status: {}",
                match.matchId,
                acceptedMatch.opponents.map { "${it.userId}(${it.status})" }
            )
            matchRepository.save(acceptedMatch)
            matchNotificationService.notifyOpponentAccepted(acceptedMatch, user.userId)
        } else {
            logger.info(
                "Match {} fully accepted by all opponents. Creating stage for claim '{}'",
                match.matchId, match.claim.title
            )
            stageService.createStage(acceptedMatch)

            val allAcceptedMatch = acceptedMatch.copy(status = MatchStatus.ACCEPTED)
            matchRepository.save(allAcceptedMatch)
            matchNotificationService.notifyMatchConfirmedAll(allAcceptedMatch)
        }
    }

    fun getQueue(): List<MatchRequest> = matchQueueRepository.findAll()

    fun getMatch(userId: String): Match? {
        return matchRepository.findByUserId(userId)
    }

    fun findAllMatches(): List<Match> {
        return matchRepository.findAll()
    }

    fun getMatchingState(user: UserModel): MatchingState =
        matchRepository.findByUserId(user.userId)
            ?.let { match ->
                MatchingState(status = MATCHED, matches = listOf(match))
            }
            ?: let {
                if (matchQueueRepository.find(user.userId) != null) {
                    MatchingState(status = MATCHING)
                } else {
                    MatchingState(status = NOT_MATCHING)
                }
            }

    data class MatchingState(
        val status: MatchingStatus,
        val matches: List<Match> = emptyList()
    )

    enum class MatchingStatus {
        MATCHING, MATCHED, NOT_MATCHING
    }

    private fun cleanupExpiredMatches() {
        val now = Instant.now(clock)
        val expirationThreshold = now.minusSeconds(settings.getMatchTtl() - 1)

        val expiredMatches = matchRepository.findAll()
            .filter { it.updatedAt.isBefore(expirationThreshold) }

        if (expiredMatches.isNotEmpty()) {
            logger.info("Found {} expired matches to clean up", expiredMatches.size)

            expiredMatches.forEach { match ->
                matchNotificationService.notifyMatchTimeout(match)

                try {
                    match.opponents.forEach { opponent ->
//                        if (opponent.ignores < 3) {
//                            val user = userRepository.getById(opponent.userId)
//                            matchQueueRepository.save(buildMatchRequest(user, ignores = opponent.ignores + 1))
//                        } else {
//                            logger.error("Too many ignores, remove from the queue.")
//                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error cleaning up expired match ${match.matchId}", e)
                }

                // Delete the expired match
                matchRepository.delete(match.matchId)
            }
        }
    }

    fun runMatching() {
        cleanupExpiredMatches()

        val waitingUsers = matchQueueRepository.findAll().sortedBy { it.joinedAt }
        val matchedUsers = mutableSetOf<String>()

//        logger.info("Starting matching cycle: {} users in queue", waitingUsers.size)
        waitingUsers.forEach { user ->
            logger.debug(
                "User {} in queue: {} claims, {} skipped, waiting since {}",
                user.userId, user.claimIdToStance.size, user.skipClaimIds.size, user.joinedAt
            )
        }

        // Phase 1: Try to match users by their existing claims with random selection
        logger.debug("Phase 1: Matching users by common claims with opposite stances")
        for (i in 0 until waitingUsers.size) {
            val userA = waitingUsers[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until waitingUsers.size) {
                val userB = waitingUsers[j]
                if (userB.userId in matchedUsers) continue

                // Find all matching claim IDs where users have opposite stances
                val matchingClaimIds = userA.claimIdToStance.keys.intersect(userB.claimIdToStance.keys)
                    .filter { claimId ->
                        val stanceA = userA.claimIdToStance[claimId]
                        val stanceB = userB.claimIdToStance[claimId]
                        areOpposite(stanceA, stanceB)
                    }
                    .filter { claimId ->
                        // Exclude claims that are in skipClaimIds for either user
                        claimId !in userA.skipClaimIds && claimId !in userB.skipClaimIds
                    }

                if (matchingClaimIds.isNotEmpty()) {
                    // Use weighted random selection giving priority to claims closer to the beginning
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

        // Phase 2: Try to match remaining users using individual user stances
        val remainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        logger.debug("Phase 1 complete: {} users matched, {} remaining", matchedUsers.size, remainingUsers.size)
        if (remainingUsers.size >= 2) {
            logger.debug("Phase 2: Matching users by individual stances with assignment")
            matchWithUserStances(remainingUsers, matchedUsers)
        }

        // Phase 3: Try to match remaining users using top 10 claims
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

        // Clean up queue and re-add unmatched users
        val finalRemainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        matchQueueRepository.removeAll()
        if (finalRemainingUsers.isNotEmpty()) {
            finalRemainingUsers.forEach { matchQueueRepository.save(it) }
        }

//        logger.info(
//            "Phase 3 complete. Matching cycle finished: {} users matched, {} users remain in queue",
//            matchedUsers.size, finalRemainingUsers.size
//        )
    }

    /**
     * Try to match remaining users using top 10 claims by score.
     * Assigns random stances and attempts to match users.
     */
    private fun matchWithTopClaims(remainingUsers: List<MatchRequest>, matchedUsers: MutableSet<String>) {
        // Get top 10 claims ordered by scoreTotal (highest first)
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

            // Find a suitable claim that neither user has skipped
            val suitableClaims = top10Claims.filter { claim ->
                claim.claimId !in userA.skipClaimIds && claim.claimId !in userB.skipClaimIds
            }

            if (suitableClaims.isNotEmpty()) {
                // Randomly select one claim from top suitable claims
                val selectedClaim = suitableClaims.random()

                // Assign random opposing stances to users
                val stances = listOf(ClaimStance.FOR, ClaimStance.AGAINST).shuffled()
                val userAStance = stances[0]
                val userBStance = stances[1]

                // Create modified match requests with assigned stances
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
                // No suitable claims found, put users back for next iteration
                availableUsers.add(0, userB)
                availableUsers.add(0, userA)
                break
            }
        }
    }

    /**
     * Select a claim with priority weighting - claims closer to the beginning of the list have higher priority.
     */
    private fun selectClaimWithPriority(availableClaims: List<String>, userClaimOrder: List<String>): String {
        if (availableClaims.size == 1) return availableClaims.first()

        // Create weights based on position in user's claim list (earlier = higher weight)
        val weights = availableClaims.map { claimId ->
            val position = userClaimOrder.indexOf(claimId)
            val weight = if (position >= 0) {
                // Higher weight for claims closer to beginning (position 0 gets highest weight)
                1.0 / (position + 1.0)
            } else {
                0.1 // Low weight for claims not found in user's order
            }
            claimId to weight
        }

        // Weighted random selection
        val totalWeight = weights.sumOf { it.second }
        val random = kotlin.random.Random.nextDouble() * totalWeight

        var currentWeight = 0.0
        for ((claimId, weight) in weights) {
            currentWeight += weight
            if (random <= currentWeight) {
                return claimId
            }
        }

        // Fallback to last claim if something goes wrong
        return availableClaims.last()
    }

    /**
     * Try to match users using individual user stances when they don't have common stances.
     * Picks from one user's stances and assigns an opposing stance to the other user.
     */
    private fun matchWithUserStances(remainingUsers: List<MatchRequest>, matchedUsers: MutableSet<String>) {
        val availableUsers = remainingUsers.filter { it.userId !in matchedUsers }.toMutableList()

        while (availableUsers.size >= 2) {
            val userA = availableUsers.removeFirstOrNull() ?: break
            val userB = availableUsers.removeFirstOrNull() ?: break

            // Combine both users' stances, prioritizing userA's order, then userB's
            val allUserStances = (userA.claimIdToStance.keys.toList() + userB.claimIdToStance.keys.toList()).distinct()

            // Find suitable claims that neither user has skipped
            val suitableClaims = allUserStances.filter { claimId ->
                claimId !in userA.skipClaimIds && claimId !in userB.skipClaimIds
            }

            if (suitableClaims.isNotEmpty()) {
                // Use weighted selection giving priority to claims closer to beginning of combined list
                val selectedClaimId = selectClaimWithPriority(suitableClaims, allUserStances)

                // Determine stances
                val userAStance = userA.claimIdToStance[selectedClaimId]
                val userBStance = userB.claimIdToStance[selectedClaimId]

                val finalUserAStance = userAStance ?: userBStance?.opposite() ?: ClaimStance.FOR
                val finalUserBStance = userBStance ?: userAStance?.opposite() ?: ClaimStance.AGAINST

                // Create match requests with determined stances
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
                // No suitable claims found, put users back
                availableUsers.add(0, userB)
                availableUsers.add(0, userA)
                break
            }
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
                stanceA to stanceB // already set and not EITHER
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
        matchNotificationService.notifyMatchFound(match)
    }

    private fun ClaimStance.opposite(): ClaimStance = when (this) {
        ClaimStance.FOR -> ClaimStance.AGAINST
        ClaimStance.AGAINST -> ClaimStance.FOR
        ClaimStance.EITHER -> error("No opposite for EITHER")
    }
}
