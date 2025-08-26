package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryJpaRepository
import com.debbly.server.claim.ClaimJpaRepository
import com.debbly.server.claim.ClaimSideUpdate
import com.debbly.server.claim.UserClaimSideService
import com.debbly.server.claim.model.ClaimSide
import com.debbly.server.claim.repository.UserClaimSideRepository
import com.debbly.server.match.MatchService.MatchingStatus.*
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchRequest
import com.debbly.server.match.model.MatchSideStatus
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
    private val userClaimSideRepository: UserClaimSideRepository,
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimJpaRepository,
    private val categoryJpaRepository: CategoryJpaRepository,
    private val userClaimSideService: UserClaimSideService,
    private val stageService: StageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun join(user: UserModel) {
        matchQueueRepository.save(buildMatchRequest(user))
    }

    private fun buildMatchRequest(
        user: UserModel,
        withSkipClaimIds: Collection<String>? = null,
        withClaimIdToSide: Collection<Pair<String, ClaimSide>>? = null
    ): MatchRequest {
        val activeCategoryIds = categoryJpaRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val userSides = userClaimSideRepository.findByUserId(user.userId)
            .filter { it.categoryId in activeCategoryIds }

        return MatchRequest(
            userId = user.userId,
            claimIdToSide = userSides.associate { it.claimId to it.side }.plus(withClaimIdToSide.orEmpty()),
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

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            matchQueueRepository.save(request = buildMatchRequest(userCachedRepository.getById(otherUser.userId)))
        }
    }

    fun switch(match: Match, user: UserModel) {

        val side = match.sides.first { it.userId == user.userId }
        val withClaimIdToSide = side.side
            ?.let { match.claim.claimId to it }
            ?.also { (_, side) ->
                userClaimSideService.save(
                    ClaimSideUpdate(
                        claimId = match.claim.claimId,
                        title = null,
                        side = side
                    ), user
                )
            }
            ?.let { listOf(it) }
            .orEmpty()

        removeMatch(user.userId)
        matchQueueRepository.save(buildMatchRequest(user, withClaimIdToSide = withClaimIdToSide))

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            matchQueueRepository.save(request = buildMatchRequest(userCachedRepository.getById(otherUser.userId)))
        }
    }

    fun accept(match: Match, user: UserModel) {
        val sides = match.sides.map { side ->
            if (side.userId == user.userId) side.copy(status = MatchSideStatus.ACCEPTED) else side
        }
        matchRepository.save(match.copy(sides = sides))
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

            } else if (match.sides.all { it.status == MatchSideStatus.ACCEPTED }) {
                stageService.createStage(match)
                matchRepository.save(match.copy(status = MatchStatus.ACCEPTED))

            } else if (match.createdAt.isBefore(matchDeadline)) {
                matchRepository.remove(match.matchId)
                match.sides.forEach { side ->
                    matchQueueRepository.save(buildMatchRequest(userCachedRepository.getById(side.userId)))
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
                    userA.claimIdToSide.keys.intersect(userB.claimIdToSide.keys).firstOrNull { claimId ->
                        val sideA = userA.claimIdToSide[claimId]
                        val sideB = userB.claimIdToSide[claimId]
                        areOpposite(sideA, sideB)
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

    private fun areOpposite(claimSideA: ClaimSide?, claimSideB: ClaimSide?): Boolean {
        if (claimSideA == null || claimSideB == null) return false
        if (claimSideA == ClaimSide.EITHER && (claimSideB == ClaimSide.FOR || claimSideB == ClaimSide.AGAINST)) return true
        if (claimSideB == ClaimSide.EITHER && (claimSideA == ClaimSide.FOR || claimSideA == ClaimSide.AGAINST)) return true
        if (claimSideA == ClaimSide.FOR && claimSideB == ClaimSide.AGAINST) return true
        if (claimSideA == ClaimSide.AGAINST && claimSideB == ClaimSide.FOR) return true
        return false
    }

    private fun createAndStoreMatch(userA: MatchRequest, userB: MatchRequest, claimId: String) {
        val matchId = idService.getId()
        val claim = claimRepository.findById(claimId).orElseThrow { Exception("Claim not found") }
        val userAEntity = userCachedRepository.getById(userA.userId)
        val userBEntity = userCachedRepository.getById(userB.userId)
        val now = Instant.now()

        val match = Match(
            matchId = matchId,
            claim = Match.MatchClaim(claim.claimId, claim.title),
            status = MatchStatus.PENDING,
            sides = listOf(
                Match.MatchSide(
                    userId = userAEntity.userId,
                    username = userAEntity.username,
                    avatarUrl = userAEntity.avatarUrl,
                    side = userA.claimIdToSide[claimId],
                    status = MatchSideStatus.PENDING
                ),
                Match.MatchSide(
                    userId = userBEntity.userId,
                    username = userBEntity.username,
                    avatarUrl = userBEntity.avatarUrl,
                    side = userB.claimIdToSide[claimId],
                    status = MatchSideStatus.PENDING
                )
            ),
            createdAt = now
        )

        matchRepository.save(match)
    }
}
