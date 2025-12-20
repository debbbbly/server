package com.debbly.server.match.repository

import com.debbly.server.match.model.Match
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

@Service
class MatchRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MATCH_KEY_PREFIX = "match:"
        private const val USER_MATCH_KEY_PREFIX = "user_match:"
        private const val ALL_MATCHES_SET = "matches:all"
    }

    fun save(match: Match) {
        val matchKey = "$MATCH_KEY_PREFIX${match.matchId}"
        val wasExisting = redisTemplate.hasKey(matchKey) == true

        // store the match once
        redisTemplate.opsForValue().set(matchKey, objectMapper.writeValueAsString(match))
        redisTemplate.opsForSet().add(ALL_MATCHES_SET, matchKey)

        // link each userId to this matchId
        match.opponents.forEach { opponent ->
            redisTemplate.opsForValue().set("$USER_MATCH_KEY_PREFIX${opponent.userId}", match.matchId)
        }

        logger.debug(
            "Match {}: matchId={}, opponents=[{}, {}], claim='{}', status={}",
            if (wasExisting) "updated" else "saved",
            match.matchId,
            match.opponents[0].userId,
            match.opponents[1].userId,
            match.claim.title,
            match.status
        )
    }

    fun findByUserId(userId: String): Match? {
        val matchId = redisTemplate.opsForValue().get("$USER_MATCH_KEY_PREFIX$userId") ?: return null
        val matchJson = redisTemplate.opsForValue().get("$MATCH_KEY_PREFIX$matchId") ?: return null
        return objectMapper.readValue(matchJson, Match::class.java)
    }

    fun findByMatchId(matchId: String): Match? {
        val matchJson = redisTemplate.opsForValue().get("$MATCH_KEY_PREFIX$matchId") ?: return null
        return objectMapper.readValue(matchJson, Match::class.java)
    }

    fun findAll(): List<Match> {
        val keys = redisTemplate.opsForSet().members(ALL_MATCHES_SET).orEmpty()
        return redisTemplate.opsForValue().multiGet(keys).orEmpty()
            .mapNotNull { objectMapper.readValue(it, Match::class.java) }
    }

    fun delete(matchId: String) {
        val match = findByMatchId(matchId)

        if (match == null) {
            logger.debug("Match delete: matchId={} (did not exist)", matchId)
            return
        }

        val matchKey = "$MATCH_KEY_PREFIX$matchId"
        // Remove the match data
        redisTemplate.delete(matchKey)
        redisTemplate.opsForSet().remove(ALL_MATCHES_SET, matchKey)

        // Remove user-to-match mappings
        match.opponents.forEach { opponent ->
            redisTemplate.delete("$USER_MATCH_KEY_PREFIX${opponent.userId}")
        }

        logger.debug(
            "Match deleted: matchId={}, opponents=[{}, {}], claim='{}'",
            matchId,
            match.opponents[0].userId,
            match.opponents[1].userId,
            match.claim.title
        )
    }
}
