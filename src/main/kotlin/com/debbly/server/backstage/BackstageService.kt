package com.debbly.server.backstage

import com.debbly.server.IdService
import com.debbly.server.claim.CategoryRepository
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.UserClaimStanceRepository
import com.debbly.server.user.UserEntity
import com.debbly.server.user.repository.UserJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BackstageService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val userClaimStanceRepository: UserClaimStanceRepository,
    private val userJpaRepository: UserJpaRepository,
    private val objectMapper: ObjectMapper,
    private val idService: IdService,
    private val claimRepository: ClaimRepository,
    private val categoryRepository: CategoryRepository
) {
    private val QUEUE_KEY = "match_queue"
    private val USER_MATCH_KEY_PREFIX = "user_match:"

    fun join(user: UserEntity) {
        val activeCategoryIds = categoryRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val userStances = userClaimStanceRepository.findByUserId(user.userId)
            .filter { it.categoryId in activeCategoryIds }

        val backstageHost = BackstageHost(
            hostId = user.userId,
            claimIdToStance = userStances.associate { it.claimId to it.stance },
            joinedAt = Instant.now()
        )

        val backstageUserStr = objectMapper.writeValueAsString(backstageHost)
        redisTemplate.opsForList().leftPush(QUEUE_KEY, backstageUserStr)
    }

    fun leave(user: UserEntity) {
        val listOperations = redisTemplate.opsForList()
        val queue = listOperations.range(QUEUE_KEY, 0, -1)
        queue?.forEach { userData ->
            val backstageHost = objectMapper.readValue(userData, BackstageHost::class.java)
            if (backstageHost.hostId == user.userId) {
                listOperations.remove(QUEUE_KEY, 1, userData)
            }
        }
    }

    fun getQueue(): List<BackstageHost> {
        val listOperations = redisTemplate.opsForList()
        return listOperations.range(QUEUE_KEY, 0, -1)
            ?.map { objectMapper.readValue(it, BackstageHost::class.java) }
            ?.toMutableList() ?: emptyList()
    }

    fun getMatchStatus(externalUserId: String): List<BackstageMatch> {
        val user = userJpaRepository.findByExternalUserId(externalUserId).orElseThrow { Exception("User not found") }
        val matchResultJson = redisTemplate.opsForValue().get("$USER_MATCH_KEY_PREFIX${user.userId}")
        return if (matchResultJson != null) {
            val backstageMatch = objectMapper.readValue(matchResultJson, BackstageMatch::class.java)
//            redisTemplate.delete("$USER_MATCH_KEY_PREFIX${user.userId}")
            listOf(backstageMatch)
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
            ?.map { objectMapper.readValue(it, BackstageHost::class.java) }
            ?.toMutableList() ?: return

        waitingUsers.sortBy { it.joinedAt }

        val matchedUsers = mutableSetOf<String>()

        for (i in 0 until waitingUsers.size) {
            val userA = waitingUsers[i]
            if (userA.hostId in matchedUsers) continue

            for (j in i + 1 until waitingUsers.size) {
                val userB = waitingUsers[j]
                if (userB.hostId in matchedUsers) continue

                val matchingClaimId =
                    userA.claimIdToStance.keys.intersect(userB.claimIdToStance.keys).firstOrNull { claimId ->
                        val stanceA = userA.claimIdToStance[claimId]
                        val stanceB = userB.claimIdToStance[claimId]
                        areOpposite(stanceA, stanceB)
                    }

                if (matchingClaimId != null) {
                    matchedUsers.add(userA.hostId)
                    matchedUsers.add(userB.hostId)

                    createAndStoreMatch(userA, userB, matchingClaimId)

                    break
                }
            }
        }

        val remainingUsers = waitingUsers.filter { it.hostId !in matchedUsers }
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

    private fun createAndStoreMatch(userA: BackstageHost, userB: BackstageHost, claimId: String) {
        val matchId = idService.getId()
        val claim = claimRepository.findById(claimId).orElseThrow { Exception("Claim not found") }
        val userAEntity = userJpaRepository.findById(userA.hostId).orElseThrow { Exception("User not found") }
        val userBEntity = userJpaRepository.findById(userB.hostId).orElseThrow { Exception("User not found") }

        val backstageMatchForA = BackstageMatch(
            matchId = matchId,
            claim = MatchClaim(claim.claimId, claim.title),
            claimStance = userA.claimIdToStance[claimId]!!,
            opponent = Opponent(
                user = OpponentUser(userBEntity.userId, userBEntity.username, userBEntity.avatarUrl),
                claimStance = userB.claimIdToStance[claimId]!!
            )
        )

        val backstageMatchForB = BackstageMatch(
            matchId = matchId,
            claim = MatchClaim(claim.claimId, claim.title),
            claimStance = userB.claimIdToStance[claimId]!!,
            opponent = Opponent(
                user = OpponentUser(userAEntity.userId, userAEntity.username, userAEntity.avatarUrl),
                claimStance = userA.claimIdToStance[claimId]!!
            )
        )

        val matchResultJsonA = objectMapper.writeValueAsString(backstageMatchForA)
        val matchResultJsonB = objectMapper.writeValueAsString(backstageMatchForB)

        redisTemplate.opsForValue().set("$USER_MATCH_KEY_PREFIX${userA.hostId}", matchResultJsonA)
        redisTemplate.opsForValue().set("$USER_MATCH_KEY_PREFIX${userB.hostId}", matchResultJsonB)
    }
}
