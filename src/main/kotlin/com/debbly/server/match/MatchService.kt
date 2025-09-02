package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.match.MatchService.MatchingStatus.*
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.match.model.MatchReason
import com.debbly.server.match.model.MatchRequest
import com.debbly.server.match.model.MatchStatus
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.stage.StageService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import com.debbly.server.websocket.MatchingWebSocketHandler
import com.debbly.server.websocket.MatchingMessage
import com.debbly.server.websocket.MessageType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val webSocketHandler: MatchingWebSocketHandler
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun join(user: UserModel) {
        matchQueueRepository.save(buildMatchRequest(user))
    }

    private fun buildMatchRequest(
        user: UserModel,
        withSkipClaimIds: Collection<String>? = null,
        withClaimIdToStance: Collection<Pair<String, ClaimStance>>? = null
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
            joinedAt = Instant.now()
        )
    }

    fun leave(user: UserModel) {
        matchQueueRepository.remove(userId = user.userId)
    }

    fun skip(match: Match, user: UserModel) {
        removeMatch(user.userId)
        matchQueueRepository.save(buildMatchRequest(user, setOf(match.claim.claimId)))

        match.opponents.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            matchQueueRepository.save(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun switch(match: Match, user: UserModel) {

        val userStance = match.opponents
            .firstOrNull() { it.userId == user.userId }
            ?.stance

        val withClaimIdToStance = userStance
            ?.let { match.claim.claimId to it }
            ?.also { (_, stance) ->
                userClaimService.updateUserClaimStance(
                    userId = user.userId,
                    claimId = match.claim.claimId,
                    stance = stance
                )
            }
            ?.let { listOf(it) }
            .orEmpty()

        removeMatch(user.userId)
        matchQueueRepository.save(buildMatchRequest(user, withClaimIdToStance = withClaimIdToStance))

        match.opponents.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            matchQueueRepository.save(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun accept(match: Match, user: UserModel) {
        val updatedOpponents = match.opponents.map { opponent ->
            if (opponent.userId == user.userId) opponent.copy(status = MatchOpponentStatus.ACCEPTED) else opponent
        }
        val updatedMatch = match.copy(opponents = updatedOpponents)
        matchRepository.save(updatedMatch)

        // Notify other users about the acceptance
        val otherUserIds = match.opponents.filter { it.userId != user.userId }.map { it.userId }
        webSocketHandler.sendMessageToUsers(otherUserIds, MatchingMessage(
            type = MessageType.MATCH_CONFIRMED,
            message = "Opponent has accepted the match",
            data = mapOf(
                "matchId" to match.matchId,
                "acceptedBy" to user.userId
            )
        ))
    }

    private fun removeMatch(userId: String) {
        matchRepository.remove(userId)
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

    fun runMatchingConfirmation() {
        val matches = matchRepository.findAll()
        val matchDeadline = Instant.now().minusSeconds(15)

        for (match in matches) {
            if (match.status == MatchStatus.ACCEPTED) {
                stageService.createStage(match)

            } else if (match.opponents.all { it.status == MatchOpponentStatus.ACCEPTED }) {
                stageService.createStage(match)
                matchRepository.save(match.copy(status = MatchStatus.ACCEPTED))

            } else if (match.createdAt.isBefore(matchDeadline)) {
                matchRepository.remove(match.matchId)
                match.opponents.forEach { opponent ->
                    matchQueueRepository.save(buildMatchRequest(userRepository.getById(opponent.userId)))
                }
                logger.info("Removed match: ${match.matchId} on ${match.claim.claimId}:${match.claim.title}.")
            }
        }
    }

    data class MatchingState(
        val status: MatchingStatus,
        val matches: List<Match> = emptyList()
    )

    enum class MatchingStatus {
        MATCHING, MATCHED, NOT_MATCHING
    }

    fun runMatching() {
        val waitingUsers = matchQueueRepository.findAll().sortedBy { it.joinedAt }
        val matchedUsers = mutableSetOf<String>()

        // Phase 1: Try to match users by their existing claims with random selection
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
                    
                    logger.debug("Matched users {} and {} on claim {} (selected from {} options with priority)", 
                               userA.userId, userB.userId, selectedClaimId, matchingClaimIds.size)
                    break
                }
            }
        }

        // Phase 2: Try to match remaining users using individual user stances
        val remainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        if (remainingUsers.size >= 2) {
            matchWithUserStances(remainingUsers, matchedUsers)
        }

        // Phase 3: Try to match remaining users using top 10 claims
        val stillRemainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        if (stillRemainingUsers.size >= 2) {
            matchWithTopClaims(stillRemainingUsers, matchedUsers)
        }

        // Clean up queue and re-add unmatched users
        val finalRemainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        matchQueueRepository.removeAll()
        if (finalRemainingUsers.isNotEmpty()) {
            finalRemainingUsers.forEach { matchQueueRepository.save(it) }
        }
        
        logger.info("Matching complete: {} users matched, {} users remain in queue", 
                   matchedUsers.size, finalRemainingUsers.size)
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

                createAndStoreMatch(userAWithStance, userBWithStance, selectedClaim.claimId, MatchReason.TOP_CLAIM_RANDOM)
                
                logger.debug("Fallback matched users {} ({}) and {} ({}) on top claim {} with score {}", 
                           userA.userId, userAStance, userB.userId, userBStance, 
                           selectedClaim.claimId, selectedClaim.scoreTotal)
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
                
                val finalUserAStance = userAStance ?: ClaimStance.FOR
                val finalUserBStance = userBStance ?: ClaimStance.AGAINST

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
                
                logger.debug("User-stance matched users {} ({}) and {} ({}) on claim {}", 
                           userA.userId, finalUserAStance, userB.userId, finalUserBStance, selectedClaimId)
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
        val now = Instant.now()

        val match = Match(
            matchId = matchId,
            claim = Match.MatchClaim(claim.claimId, claim.title),
            status = MatchStatus.PENDING,
            opponents = listOf(
                Match.MatchOpponent(
                    userId = userAEntity.userId,
                    username = userAEntity.username,
                    avatarUrl = userAEntity.avatarUrl,
                    stance = userA.claimIdToStance[claimId],
                    status = MatchOpponentStatus.PENDING
                ),
                Match.MatchOpponent(
                    userId = userBEntity.userId,
                    username = userBEntity.username,
                    avatarUrl = userBEntity.avatarUrl,
                    stance = userB.claimIdToStance[claimId],
                    status = MatchOpponentStatus.PENDING
                )
            ),
            createdAt = now,
            // reason = reason
        )

        matchRepository.save(match)
    }
}
