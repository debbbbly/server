package com.debbly.server.backstage

import com.debbly.server.IdService
import com.debbly.server.claim.CategoryRepository
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.ClaimStanceUpdate
import com.debbly.server.claim.UserClaimStanceService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.UserClaimStanceRepository
import com.debbly.server.user.UserEntity
import com.debbly.server.user.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BackstageService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val userClaimStanceRepository: UserClaimStanceRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val idService: IdService,
    private val claimRepository: ClaimRepository,
    private val categoryRepository: CategoryRepository,
    private val userClaimStanceService: UserClaimStanceService
) {
    private val QUEUE_KEY = "match_queue"
    private val USER_BACKSTAGE_MATCH_KEY_PREFIX = "user_backstage_match:"

    fun join(user: UserEntity) {
        pushMatchRequest(buildMatchRequest(user))
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

    private fun pushMatchRequest(request: MatchRequest) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, objectMapper.writeValueAsString(request))
    }

    private fun removeMatchRequest(userId: String) {
        val listOperations = redisTemplate.opsForList()
        val queue = listOperations.range(QUEUE_KEY, 0, -1)
        queue?.forEach { userData ->
            val matchRequest = objectMapper.readValue(userData, MatchRequest::class.java)
            if (matchRequest.userId == userId) {
                listOperations.remove(QUEUE_KEY, 1, userData)
            }
        }
    }

    fun leave(user: UserEntity) {
        removeMatchRequest(userId = user.userId)
    }

    fun skip(match: Match, user: UserEntity) {
        removeMatch(user.userId)
        pushMatchRequest(buildMatchRequest(user, setOf(match.claim.claimId)))

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            pushMatchRequest(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
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
        pushMatchRequest(buildMatchRequest(user, withClaimIdToStance = withClaimIdToStance))

        match.sides.filter { it.userId != user.userId }.forEach { otherUser ->
            removeMatch(otherUser.userId)
            pushMatchRequest(request = buildMatchRequest(userRepository.getById(otherUser.userId)))
        }
    }

    fun accept(match: Match, user: UserEntity) {

        val acceptedMatch = match.copy(status = MatchStatus.ACCEPTED)
        saveMatch(acceptedMatch, user)

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
        redisTemplate.delete("$USER_BACKSTAGE_MATCH_KEY_PREFIX${userId}")
    }

    fun getQueue(): List<MatchRequest> {
        val listOperations = redisTemplate.opsForList()
        return listOperations.range(QUEUE_KEY, 0, -1)
            ?.map { objectMapper.readValue(it, MatchRequest::class.java) }
            ?.toMutableList() ?: emptyList()
    }

    fun getMatch(userId: String): Match? {
        return redisTemplate.opsForValue().get("$USER_BACKSTAGE_MATCH_KEY_PREFIX${userId}")?.let {
            objectMapper.readValue(it, Match::class.java)
        }
    }

    fun getMatchStatus(user: UserEntity): List<Match> {

        val matchResultJson = redisTemplate.opsForValue().get("$USER_BACKSTAGE_MATCH_KEY_PREFIX${user.userId}")
        return if (matchResultJson != null) {
            val match = objectMapper.readValue(matchResultJson, Match::class.java)
//            redisTemplate.delete("$USER_MATCH_KEY_PREFIX${user.userId}")
            listOf(match)
        } else {
            emptyList()
        }
    }

    fun performMatching() {
        val listOps = redisTemplate.opsForList()
        val queueSize = listOps.size(QUEUE_KEY) ?: 0
        if (queueSize < 2) {
            return
        }

        val waitingUsers = listOps.range(QUEUE_KEY, 0, -1)
            ?.map { objectMapper.readValue(it, MatchRequest::class.java) }
            ?.toMutableList() ?: return

        waitingUsers.sortBy { it.joinedAt }

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
        redisTemplate.delete(QUEUE_KEY)
        if (remainingUsers.isNotEmpty()) {
            val remainingUsersJson = remainingUsers.map { objectMapper.writeValueAsString(it) }
            listOps.leftPushAll(QUEUE_KEY, remainingUsersJson)
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

        saveMatch(match, userAEntity)
        saveMatch(match, userBEntity)
    }

    private fun saveMatch(
        match: Match,
        user: UserEntity
    ) {
        redisTemplate.opsForValue().set(
            "$USER_BACKSTAGE_MATCH_KEY_PREFIX${user.userId}",
            objectMapper.writeValueAsString(match)
        )
    }
}
