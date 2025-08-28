package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.ClaimStance
import com.debbly.server.claim.user.ClaimStanceUpdate
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.match.MatchService.MatchingStatus.*
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.match.model.MatchRequest
import com.debbly.server.match.model.MatchStatus
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.stage.StageService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
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
    private val stageService: StageService
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
            claimIdToStance = userClaims.associate { it.claim.claimId to it.stance }.plus(withClaimIdToStance.orEmpty()),
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
                userClaimService.save(
                    ClaimStanceUpdate(
                        claimId = match.claim.claimId,
                        title = null,
                        stance = stance
                    ), user
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
        val opponents = match.opponents.map { opponent ->
            if (opponent.userId == user.userId) opponent.copy(status = MatchOpponentStatus.ACCEPTED) else opponent
        }
        matchRepository.save(match.copy(opponents = opponents))
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

        // logger.info("Running matching for ${waitingUsers.size} users")

        val matchedUsers = mutableSetOf<String>()

        for (i in 0 until waitingUsers.size) {
            val userA = waitingUsers[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until waitingUsers.size) {
                val userB = waitingUsers[j]
                if (userB.userId in matchedUsers) continue

                val matchingClaimId =
                    userA.claimIdToStance.keys.intersect(userB.claimIdToStance.keys).firstOrNull { claimId ->
                        val opponentA = userA.claimIdToStance[claimId]
                        val opponentB = userB.claimIdToStance[claimId]
                        areOpposite(opponentA, opponentB)
                    }

                if (matchingClaimId != null) {
                    matchedUsers.add(userA.userId)
                    matchedUsers.add(userB.userId)

                    createAndStoreMatch(userA, userB, matchingClaimId)

                    break
                }
            }
        }

        val remainingUsers = waitingUsers.filter { it.userId !in matchedUsers }
        matchQueueRepository.removeAll()
        if (remainingUsers.isNotEmpty()) {
            remainingUsers.forEach { matchQueueRepository.save(it) }
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

    private fun createAndStoreMatch(userA: MatchRequest, userB: MatchRequest, claimId: String) {
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
            createdAt = now
        )

        matchRepository.save(match)
    }
}
