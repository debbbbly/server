package com.debbly.server.backstage.repository

import com.debbly.server.backstage.MatchRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class QueueRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val QUEUE_KEY = "match_queue"

    fun push(request: MatchRequest) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, objectMapper.writeValueAsString(request))
    }

    fun pushAll(request: List<MatchRequest>) {
        redisTemplate.opsForList().leftPushAll(QUEUE_KEY, request.map { objectMapper.writeValueAsString(it) })
    }

    fun removeByUserId(userId: String) {
        val listOperations = redisTemplate.opsForList()
        val queue = listOperations.range(QUEUE_KEY, 0, -1)
        queue?.forEach { userData ->
            val matchRequest = objectMapper.readValue(userData, MatchRequest::class.java)
            if (matchRequest.userId == userId) {
                listOperations.remove(QUEUE_KEY, 1, userData)
            }
        }
    }

    fun findAll(): List<MatchRequest> {
        val listOperations = redisTemplate.opsForList()
        return listOperations.range(QUEUE_KEY, 0, -1)
            ?.map { objectMapper.readValue(it, MatchRequest::class.java) }
            ?.toMutableList() ?: emptyList()
    }

    fun count() = redisTemplate.opsForList().size(QUEUE_KEY) ?: 0

    fun removeAll() = redisTemplate.delete(QUEUE_KEY)
}
