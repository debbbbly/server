package com.debbly.server.match.repository

import com.debbly.server.match.model.MatchRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class MatchQueueRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val USER_MATCH_REQUEST_PREFIX = "user_match_request:"
        private const val USER_MATCH_REQUEST_KEYS = "user_match_requests:all"
    }

    fun save(request: MatchRequest) {
        val key = "$USER_MATCH_REQUEST_PREFIX${request.userId}"
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(request))
        redisTemplate.opsForSet().add(USER_MATCH_REQUEST_KEYS, key)
    }

    fun remove(userId: String) {
        val key = "$USER_MATCH_REQUEST_PREFIX$userId"
        redisTemplate.delete(key)
        redisTemplate.opsForSet().remove(USER_MATCH_REQUEST_KEYS, key)
    }

    fun find(userId: String): MatchRequest? {
        return redisTemplate.opsForValue().get("$USER_MATCH_REQUEST_PREFIX$userId")?.let {
            objectMapper.readValue(it, MatchRequest::class.java)
        }
    }

    fun findAll(): List<MatchRequest> {
        val keys = redisTemplate.opsForSet().members(USER_MATCH_REQUEST_KEYS).orEmpty()
        return redisTemplate.opsForValue().multiGet(keys).orEmpty()
            .mapNotNull {
                objectMapper.readValue(it, MatchRequest::class.java)
            }
    }

    fun count(): Long {
        return redisTemplate.opsForSet().size(USER_MATCH_REQUEST_KEYS) ?: 0
    }

    fun removeAll() {
        val keys = redisTemplate.opsForSet().members(USER_MATCH_REQUEST_KEYS).orEmpty()
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
            redisTemplate.delete(USER_MATCH_REQUEST_KEYS)
        }
    }
}