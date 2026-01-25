package com.debbly.server.match.repository

import com.debbly.server.match.model.MatchRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class MatchQueueRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val USER_MATCH_REQUEST_PREFIX = "user_match_request:"
        private const val USER_MATCH_REQUEST_KEYS = "user_match_requests:all"
    }

    fun save(request: MatchRequest) {
        val key = "$USER_MATCH_REQUEST_PREFIX${request.userId}"
        val wasExisting = redisTemplate.hasKey(key) == true

        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(request))
        redisTemplate.opsForSet().add(USER_MATCH_REQUEST_KEYS, key)

        logger.debug(
            "Queue save: userId={}, {} (claims={}, topics={}, skipped={})",
            request.userId,
            if (wasExisting) "updated" else "added",
            request.claims.size,
            request.topics.size,
            request.skipClaimIds.size
        )
    }

    fun remove(userId: String) {
        val key = "$USER_MATCH_REQUEST_PREFIX$userId"
        val existed = redisTemplate.hasKey(key) == true

        redisTemplate.delete(key)
        redisTemplate.opsForSet().remove(USER_MATCH_REQUEST_KEYS, key)

        if (existed) {
            logger.debug("Queue remove: userId={} (existed)", userId)
        } else {
            logger.debug("Queue remove: userId={} (did not exist)", userId)
        }
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
            logger.debug("Queue removeAll: clearing {} entries", keys.size)
            redisTemplate.delete(keys)
            redisTemplate.delete(USER_MATCH_REQUEST_KEYS)
        } else {
            logger.debug("Queue removeAll: queue already empty")
        }
    }
}