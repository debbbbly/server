package com.debbly.server.backstage

import com.debbly.server.IdService
import com.debbly.server.backstage.BackstageService.MatchingStatus.*
import com.debbly.server.backstage.model.Match
import com.debbly.server.backstage.model.MatchRequest
import com.debbly.server.backstage.model.MatchSideStatus
import com.debbly.server.backstage.model.MatchStatus
import com.debbly.server.backstage.repository.MatchQueueRepository
import com.debbly.server.backstage.repository.MatchRepository
import com.debbly.server.claim.CategoryRepository
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.ClaimStanceUpdate
import com.debbly.server.claim.UserClaimStanceService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.UserClaimStanceRepository
import com.debbly.server.stage.StageService
import com.debbly.server.user.UserEntity
import com.debbly.server.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BackstageService(
    private val userClaimStanceRepository: UserClaimStanceRepository,
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val idService: IdService,
    private val claimRepository: ClaimRepository,
    private val categoryRepository: CategoryRepository,
    private val userClaimStanceService: UserClaimStanceService,
    private val stageService: StageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun join(user: UserEntity) {
        matchQueueRepository.save(buildMatchRequest(user))
    }

    private fun buildMatchRequest(
        user: UserEntity,
        withSkipClaimIds: Collection<String>? = null,
        withClaimIdToStance: Collection<Pair<String, ClaimStance>>? = null
    ): MatchRequest {
        val activeCategoryIds = categoryRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val userStances = userClaimStanceRepository.findByUserId(user.userId)
            .filter { it.categoryId in activeCategoryIds }

        return MatchRequest(
            userId = user.userId,
            claimIdToStance = userStances.associate { it.claimId to it.stance }.plus(withClaimIdToStance.orEmpty()),
            skipClaimIds = withSkipClaimIds.orEmpty(),
            joinedAt = Instant.now()
        )
    }

    fun leave(user: UserEntity) {
        matchQueueRepository.remove(userId = user.userId)
    }

    fun skip(match: Match, user: UserEntity) {
        removeMatch(user.userId)
        matchQueueRepository.save(buildMatchRequest(user, setOf(match.claim.claimId)))

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            matchQueueRepository.save(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun switch(match: Match, user: UserEntity) {

        val side = match.sides.first { it.userId == user.userId }
        val withClaimIdToStance = side.stance
            ?.let { match.claim.claimId to it }
            ?.also { (_, stance) ->
                userClaimStanceService.save(
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

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            matchQueueRepository.save(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun accept(match: Match, user: UserEntity) {
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

    fun getMatchingState(user: UserEntity): MatchingState =
        matchRepository.findByUserId(user.userId)
            ?.let {
                match -> MatchingState(status = MATCHED, matches = listOf(match))
            }
            ?: let {
                if (matchQueueRepository.find(user.userId) != null) {
                    MatchingState(status = MATCHING)
                } else {
                    MatchingState(status = NOT_ENABLED)
                }
            }

    fun runMatchingConfirmation() {
        val matches = matchRepository.findAll()
        val matchDeadline = Instant.now().minusSeconds(15)

        for (match in matches) {
            if (match.status == MatchStatus.ACCEPTED) {

                // check stage status

            } else if (match.sides.all { it.status == MatchSideStatus.ACCEPTED }) {
                stageService.createStage(match)
                matchRepository.save(match.copy(status = MatchStatus.ACCEPTED))

            } else if (match.createdAt.isBefore(matchDeadline)) {
                matchRepository.remove(match.matchId)
                match.sides.forEach { side ->
                    matchQueueRepository.save(buildMatchRequest(userRepository.getById(side.userId)))
                }
            }
        }
    }

    data class MatchingState(
        val status: MatchingStatus,
        val matches: List<Match> = emptyList()
    )

    enum class MatchingStatus {
        MATCHING, MATCHED, NOT_ENABLED
    }

    fun runMatching() {

        val waitingUsers = matchQueueRepository.findAll().sortedBy { it.joinedAt }

        logger.info("Running matching for ${waitingUsers.size} users")

        val matchedUsers = mutableSetOf<String>()

        for (i in 0 until waitingUsers.size) {
            val userA = waitingUsers[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until waitingUsers.size) {
                val userB = waitingUsers[j]
                if (userB.userId in matchedUsers) continue

                val matchingClaimId =
                    userA.claimIdToStance.keys.intersect(userB.claimIdToStance.keys).firstOrNull { claimId ->
                        val stanceA = userA.claimIdToStance[claimId]
                        val stanceB = userB.claimIdToStance[claimId]
                        areOpposite(stanceA, stanceB)
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
        if (claimStanceA == ClaimStance.ANY && (claimStanceB == ClaimStance.PRO || claimStanceB == ClaimStance.CON)) return true
        if (claimStanceB == ClaimStance.ANY && (claimStanceA == ClaimStance.PRO || claimStanceA == ClaimStance.CON)) return true
        if (claimStanceA == ClaimStance.PRO && claimStanceB == ClaimStance.CON) return true
        if (claimStanceA == ClaimStance.CON && claimStanceB == ClaimStance.PRO) return true
        return false
    }

    private fun createAndStoreMatch(userA: MatchRequest, userB: MatchRequest, claimId: String) {
        val matchId = idService.getId()
        val claim = claimRepository.findById(claimId).orElseThrow { Exception("Claim not found") }
        val userAEntity = userRepository.getById(userA.userId)
        val userBEntity = userRepository.getById(userB.userId)
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
                    stance = userA.claimIdToStance[claimId],
                    status = MatchSideStatus.PENDING
                ),
                Match.MatchSide(
                    userId = userBEntity.userId,
                    username = userBEntity.username,
                    avatarUrl = userBEntity.avatarUrl,
                    stance = userB.claimIdToStance[claimId],
                    status = MatchSideStatus.PENDING
                )
            ),
            createdAt = now
        )

        matchRepository.save(match)
    }
}
