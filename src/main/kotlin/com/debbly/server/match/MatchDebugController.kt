package com.debbly.server.match

import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/debug/match")
class MatchDebugController(
    private val matchRepository: MatchRepository,
    private val matchQueueRepository: MatchQueueRepository,
    private val redisTemplate: RedisTemplate<String, String>
) {

    @GetMapping("/redis-state")
    fun getRedisState(): Map<String, Any> {
        val allMatches = matchRepository.findAll()
        val allQueueRequests = matchQueueRepository.findAll()

        // Get all Redis keys related to matching
        val allKeys = redisTemplate.keys("*match*")?.sorted() ?: emptyList()

        return mapOf(
            "matches" to allMatches,
            "queue" to allQueueRequests
        )
    }

    @DeleteMapping("/clear-all-matches")
    fun clearAllMatches(): Map<String, Any> {
        val matches = matchRepository.findAll()
        matches.forEach { match ->
            matchRepository.delete(match.matchId)
        }

        return mapOf(
            "deleted" to matches.size,
            "matchIds" to matches.map { it.matchId }
        )
    }

    @DeleteMapping("/clear-queue")
    fun clearQueue(): Map<String, Any> {
        val beforeCount = matchQueueRepository.count()
        matchQueueRepository.removeAll()
        val afterCount = matchQueueRepository.count()

        return mapOf(
            "clearedCount" to beforeCount,
            "remainingCount" to afterCount
        )
    }

    @DeleteMapping("/clear-all-redis")
    fun clearAllRedis(): Map<String, Any> {
        val keys = redisTemplate.keys("*match*")?.toList() ?: emptyList()
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }

        return mapOf(
            "deletedKeys" to keys.size,
            "keys" to keys
        )
    }
}
