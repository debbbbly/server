package com.debbly.server.backstage.repository

import com.debbly.server.backstage.model.MatchRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class MatchQueueRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val USER_QUEUE_KEY_PREFIX = "user_match_request:"
        private const val ALL_REQUESTS_SET = "user_match_requests:all"
    }

    fun save(request: MatchRequest) {
        val key = "$USER_QUEUE_KEY_PREFIX${request.userId}"
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(request))
        redisTemplate.opsForSet().add(ALL_REQUESTS_SET, key)
    }

    fun remove(userId: String) {
        val key = "$USER_QUEUE_KEY_PREFIX$userId"
        redisTemplate.delete(key)
        redisTemplate.opsForSet().remove(ALL_REQUESTS_SET, key)
    }

    fun find(userId: String): MatchRequest? {
        return redisTemplate.opsForValue().get("$USER_QUEUE_KEY_PREFIX$userId")?.let {
            objectMapper.readValue(it, MatchRequest::class.java)
        }
    }

    fun findAll(): List<MatchRequest> {
        val keys = redisTemplate.opsForSet().members(ALL_REQUESTS_SET).orEmpty()
        return redisTemplate.opsForValue().multiGet(keys).orEmpty()
            .mapNotNull { objectMapper.readValue(it, MatchRequest::class.java) }
    }

    fun count(): Long {
        return redisTemplate.opsForSet().size(ALL_REQUESTS_SET) ?: 0
    }

    fun removeAll() {
        val keys = redisTemplate.opsForSet().members(ALL_REQUESTS_SET).orEmpty()
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
            redisTemplate.delete(ALL_REQUESTS_SET)
        }
    }
}