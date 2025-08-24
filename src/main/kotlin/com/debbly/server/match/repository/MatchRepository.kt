package com.debbly.server.match.repository

import com.debbly.server.match.model.Match
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class MatchRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MATCH_KEY_PREFIX = "match:"
        private const val USER_MATCH_KEY_PREFIX = "user_match:"
        private const val ALL_MATCHES_SET = "matches:all"
    }

    fun save(match: Match) {
        val matchKey = "$MATCH_KEY_PREFIX${match.matchId}"
        // store the match once
        redisTemplate.opsForValue().set(matchKey, objectMapper.writeValueAsString(match))
        redisTemplate.opsForSet().add(ALL_MATCHES_SET, matchKey)

        // link each userId to this matchId
        match.sides.forEach { side ->
            redisTemplate.opsForValue().set("$USER_MATCH_KEY_PREFIX${side.userId}", match.matchId)
        }
    }

    fun findByUserId(userId: String): Match? {
        val matchId = redisTemplate.opsForValue().get("$USER_MATCH_KEY_PREFIX$userId") ?: return null
        val matchJson = redisTemplate.opsForValue().get("$MATCH_KEY_PREFIX$matchId") ?: return null
        return objectMapper.readValue(matchJson, Match::class.java)
    }

    fun findByMatchId(matchId: String): Match? {
        return redisTemplate.opsForValue().get("$MATCH_KEY_PREFIX$matchId")?.let {
            objectMapper.readValue(it, Match::class.java)
        }
    }

    fun findAll(): List<Match> {
        val keys = redisTemplate.opsForSet().members(ALL_MATCHES_SET).orEmpty()
        return redisTemplate.opsForValue().multiGet(keys).orEmpty()
            .mapNotNull { objectMapper.readValue(it, Match::class.java) }
    }

    fun remove(matchId: String) {
        val matchKey = "$MATCH_KEY_PREFIX$matchId"
        redisTemplate.opsForValue().get(matchKey)
            ?.let { objectMapper.readValue(it, Match::class.java) }
            ?.sides
            ?.forEach { userId ->
                redisTemplate.delete("$USER_MATCH_KEY_PREFIX$userId")
            }

        redisTemplate.delete(matchKey)
        redisTemplate.opsForSet().remove(ALL_MATCHES_SET, matchKey)
    }
}
