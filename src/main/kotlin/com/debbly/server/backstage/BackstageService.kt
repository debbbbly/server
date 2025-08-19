package com.debbly.server.backstage

import com.debbly.server.IdService
import com.debbly.server.backstage.model.Match
import com.debbly.server.backstage.model.MatchStatus
import com.debbly.server.backstage.repository.MatchRepository
import com.debbly.server.backstage.repository.QueueRepository
import com.debbly.server.claim.CategoryRepository
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.ClaimStanceUpdate
import com.debbly.server.claim.UserClaimStanceService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.UserClaimStanceRepository
import com.debbly.server.user.UserEntity
import com.debbly.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BackstageService(
    private val userClaimStanceRepository: UserClaimStanceRepository,
    private val queueRepository: QueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val idService: IdService,
    private val claimRepository: ClaimRepository,
    private val categoryRepository: CategoryRepository,
    private val userClaimStanceService: UserClaimStanceService
) {
    fun join(user: UserEntity) {
        queueRepository.push(buildMatchRequest(user))
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
        queueRepository.removeByUserId(userId = user.userId)
    }

    fun skip(match: Match, user: UserEntity) {
        removeMatch(user.userId)
        queueRepository.push(buildMatchRequest(user, setOf(match.claim.claimId)))

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            queueRepository.push(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun switch(match: Match, user: UserEntity) {

        val side = match.sides.first { it.userId == user.userId }
        val withClaimIdToStance = side.stance
            ?.let { match.claim.claimId to it }
            ?.also { (_, stance) ->
                userClaimStanceService.save(ClaimStanceUpdate(match.claim.claimId, null, stance), user)
            }
            ?.let { listOf(it) }
            .orEmpty()

        removeMatch(user.userId)
        queueRepository.push(buildMatchRequest(user, withClaimIdToStance = withClaimIdToStance))

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            queueRepository.push(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun accept(match: Match, user: UserEntity) {

        val acceptedMatch = match.copy(status = MatchStatus.ACCEPTED)
        matchRepository.save(user.userId, acceptedMatch)

        val allAccepted = match.sides
            .filter { it.userId != user.userId }
            .mapNotNull { otherUser -> getMatch(otherUser.userId) }
            .all { otherMatch ->
                otherMatch.status == MatchStatus.ACCEPTED
            }

        if (allAccepted) {

            // STAGE !!!

        }

    }

    private fun removeMatch(userId: String) {
        matchRepository.remove(userId)
    }


    fun getQueue(): List<MatchRequest> = queueRepository.findAll()

    fun getMatch(userId: String): Match? {
        return matchRepository.find(userId)
    }

    fun getMatchStatus(user: UserEntity): List<Match> {

        return matchRepository.find(user.userId)?.let { match ->
            listOf(match)
        } ?: emptyList()
    }

    fun performMatching() {
        if (queueRepository.count() < 2) {
            return
        }

        val waitingUsers = queueRepository.findAll().sortedBy { it.joinedAt } ?: return

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
        queueRepository.removeAll()
        if (remainingUsers.isNotEmpty()) {
            queueRepository.pushAll(remainingUsers)
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

        val match = Match(
            matchId = matchId,
            claim = Match.MatchClaim(claim.claimId, claim.title),
            status = MatchStatus.PENDING,
            sides = listOf(
                Match.MatchSide(
                    userId = userAEntity.userId,
                    username = userAEntity.username,
                    avatarUrl = userAEntity.avatarUrl,
                    stance = userA.claimIdToStance[claimId]
                ),
                Match.MatchSide(
                    userId = userBEntity.userId,
                    username = userBEntity.username,
                    avatarUrl = userBEntity.avatarUrl,
                    stance = userB.claimIdToStance[claimId]
                )
            )
        )

        matchRepository.save(userAEntity.userId, match)
        matchRepository.save(userBEntity.userId, match)
    }
}
