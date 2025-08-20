package com.debbly.server.backstage.repository

import com.debbly.server.backstage.model.Match
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class MatchRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val USER_BACKSTAGE_MATCH_KEY_PREFIX = "user_match:"
        private const val USER_BACKSTAGE_MATCH_ALL = "${USER_BACKSTAGE_MATCH_KEY_PREFIX}all"
    }

    fun save(userId: String, match: Match) {
        val key = "$USER_BACKSTAGE_MATCH_KEY_PREFIX$userId"
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(match))

        redisTemplate.opsForSet().add(USER_BACKSTAGE_MATCH_ALL, key)
    }

    fun find(userId: String): Match? {
        return redisTemplate.opsForValue().get("$USER_BACKSTAGE_MATCH_KEY_PREFIX${userId}")?.let {
            objectMapper.readValue(it, Match::class.java)
        }
    }

    fun findAll(): List<Match> {
        val keys = redisTemplate.opsForSet().members(USER_BACKSTAGE_MATCH_ALL).orEmpty()
        return redisTemplate.opsForValue().multiGet(keys).orEmpty()
            .mapNotNull { objectMapper.readValue(it, Match::class.java) }
    }

    fun remove(userId: String) {
        val key = "$USER_BACKSTAGE_MATCH_KEY_PREFIX$userId"
        redisTemplate.delete(key)
        redisTemplate.opsForSet().remove(USER_BACKSTAGE_MATCH_ALL, key)
    }
}
