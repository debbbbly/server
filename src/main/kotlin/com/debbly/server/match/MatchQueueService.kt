package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.ClaimStanceRepository
import com.debbly.server.claim.ClaimStance
import com.debbly.server.user.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class MatchQueueService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val claimStanceRepository: ClaimStanceRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val idService: IdService,
    private val claimRepository: ClaimRepository
) {
    private val QUEUE_KEY = "match_queue"
    private val USER_MATCH_KEY_PREFIX = "user_match:"

    fun joinQueue(externalUserId: String) {
        val user = userRepository.findByExternalUserId(externalUserId).orElseThrow { Exception("User not found") }
        val stances = claimStanceRepository.findByIdUserId(user.userId)

        val queueUser = QueueUser(
            userId = user.userId,
            stances = stances.associate { it.id.claimId to it.stance },
            joinedAt = Instant.now()
        )

        val userData = objectMapper.writeValueAsString(queueUser)
        redisTemplate.opsForList().leftPush(QUEUE_KEY, userData)
    }

    fun leaveQueue(externalUserId: String) {
        val user = userRepository.findByExternalUserId(externalUserId).orElseThrow { Exception("User not found") }
        val listOperations = redisTemplate.opsForList()
        val queue = listOperations.range(QUEUE_KEY, 0, -1)
        queue?.forEach { userData ->
            val queueUser = objectMapper.readValue(userData, QueueUser::class.java)
            if (queueUser.userId == user.userId) {
                listOperations.remove(QUEUE_KEY, 1, userData)
            }
        }
    }

    fun getMatchStatus(externalUserId: String): List<MatchResult> {
        val user = userRepository.findByExternalUserId(externalUserId).orElseThrow { Exception("User not found") }
        val matchResultJson = redisTemplate.opsForValue().get("$USER_MATCH_KEY_PREFIX${user.userId}")
        return if (matchResultJson != null) {
            val matchResult = objectMapper.readValue(matchResultJson, MatchResult::class.java)
//            redisTemplate.delete("$USER_MATCH_KEY_PREFIX${user.userId}")
            listOf(matchResult)
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
            ?.map { objectMapper.readValue(it, QueueUser::class.java) }
            ?.toMutableList() ?: return
        
        waitingUsers.sortBy { it.joinedAt }

        val matchedUsers = mutableSetOf<String>()

        for (i in 0 until waitingUsers.size) {
            val userA = waitingUsers[i]
            if (userA.userId in matchedUsers) continue

            for (j in i + 1 until waitingUsers.size) {
                val userB = waitingUsers[j]
                if (userB.userId in matchedUsers) continue

                val matchingClaimId = userA.stances.keys.intersect(userB.stances.keys).firstOrNull { claimId ->
                    val stanceA = userA.stances[claimId]
                    val stanceB = userB.stances[claimId]
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

    private fun createAndStoreMatch(userA: QueueUser, userB: QueueUser, claimId: String) {
        val matchId = idService.getId()
        val claim = claimRepository.findById(claimId).orElseThrow { Exception("Claim not found") }
        val userAEntity = userRepository.findById(userA.userId).orElseThrow { Exception("User not found") }
        val userBEntity = userRepository.findById(userB.userId).orElseThrow { Exception("User not found") }

        val matchResultForA = MatchResult(
            matchId = matchId,
            claim = MatchClaim(claim.claimId, claim.title),
            claimStance = userA.stances[claimId]!!,
            opponent = Opponent(
                user = OpponentUser(userBEntity.userId, userBEntity.username, userBEntity.avatarUrl),
                claimStance = userB.stances[claimId]!!
            )
        )

        val matchResultForB = MatchResult(
            matchId = matchId,
            claim = MatchClaim(claim.claimId, claim.title),
            claimStance = userB.stances[claimId]!!,
            opponent = Opponent(
                user = OpponentUser(userAEntity.userId, userAEntity.username, userAEntity.avatarUrl),
                claimStance = userA.stances[claimId]!!
            )
        )

        val matchResultJsonA = objectMapper.writeValueAsString(matchResultForA)
        val matchResultJsonB = objectMapper.writeValueAsString(matchResultForB)

        redisTemplate.opsForValue().set("$USER_MATCH_KEY_PREFIX${userA.userId}", matchResultJsonA)
        redisTemplate.opsForValue().set("$USER_MATCH_KEY_PREFIX${userB.userId}", matchResultJsonB)
    }
}
