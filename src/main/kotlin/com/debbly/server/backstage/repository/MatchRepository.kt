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
    private val USER_BACKSTAGE_MATCH_KEY_PREFIX = "user_backstage_match:"

    fun save(userId: String, match: Match) {
        redisTemplate.opsForValue().set(
            "$USER_BACKSTAGE_MATCH_KEY_PREFIX${userId}",
            objectMapper.writeValueAsString(match)
        )
    }

    fun find(userId: String): Match? {
        return redisTemplate.opsForValue().get("$USER_BACKSTAGE_MATCH_KEY_PREFIX${userId}")?.let {
            objectMapper.readValue(it, Match::class.java)
        }
    }

    fun remove(userId: String) {
        redisTemplate.delete("$USER_BACKSTAGE_MATCH_KEY_PREFIX${userId}")
    }

}
