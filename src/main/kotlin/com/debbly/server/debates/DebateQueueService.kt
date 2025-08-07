package com.debbly.server.debates

import com.debbly.server.claim.ClaimService
import com.debbly.server.user.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class DebateQueueService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val claimService: ClaimService,
    private val userService: UserService,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val QUEUE_KEY = "debate:queue"
    }

    fun joinQueue(userId: String): QueueStatusResponse {
        val size = redisTemplate.opsForList().size(QUEUE_KEY) ?: 0
        if (size > 0) {
            val opponentUserId = redisTemplate.opsForList().leftPop(QUEUE_KEY)
            if (opponentUserId != null) {
                val opponent = userService.findById(opponentUserId)
                if (opponent != null) {
                    val claims = claimService.findAll()
                    val randomClaim = if (claims.isNotEmpty()) claims.random().text else "Default debate topic"
                    val sides = listOf(DebateSide.PRO, DebateSide.CON).shuffled()
                    
                    return QueueStatusResponse(
                        listOf(
                            DebateMatch(
                                randomClaim,
                                randomClaim,
                                sides[0],
                                OpponentInfo(opponentUserId, opponent.username, "https://yt3.ggpht.com/xqV0eqDdQCpTGx4GfsnDrPgoKQz8…A2lNVR6c5p7LCJuveFY5JId=s88-c-k-c0x00ffffff-no-rj")
                            )
                        )
                    )
                }
            }
        }

        redisTemplate.opsForList().rightPush(QUEUE_KEY, userId)

        return QueueStatusResponse(emptyList())
    }

    fun getStatus(userId: String): QueueStatusResponse {
        val size = redisTemplate.opsForList().size(QUEUE_KEY) ?: 0
        if (size > 0) {
            val opponentUserId = redisTemplate.opsForList().leftPop(QUEUE_KEY)
            if (opponentUserId != null) {
                val opponent = userService.findById(opponentUserId)
                if (opponent != null) {
                    val claims = claimService.findAll()
                    val randomClaim = if (claims.isNotEmpty()) claims.random().text else "Default debate topic"
                    val sides = listOf(DebateSide.PRO, DebateSide.CON).shuffled()
                    
                    return QueueStatusResponse(
                        listOf(
                            DebateMatch(
                                randomClaim,
                                randomClaim,
                                sides[0],
                                OpponentInfo(opponentUserId, opponent.username, "https://yt3.ggpht.com/xqV0eqDdQCpTGx4GfsnDrPgoKQz8…A2lNVR6c5p7LCJuveFY5JId=s88-c-k-c0x00ffffff-no-rj")
                            )
                        )
                    )
                }
            }
        }

        return QueueStatusResponse(emptyList())
    }

    fun leaveQueue(userId: String): QueueStatusResponse {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId)

        return QueueStatusResponse(emptyList())
    }
}