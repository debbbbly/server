package com.debbly.server.viewer

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

enum class ViewerScope {
    STAGE, EVENT;

    fun redisKey(scopeId: String) = "viewers:${name.lowercase()}:$scopeId"
}

@Service
class ViewerService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    companion object {
        // Client should call /view every ~60s; 90s gives slack before dropping out
        private const val VIEWER_TTL_SECONDS = 90L
    }

    fun trackViewer(scope: ViewerScope, scopeId: String, viewerId: String) {
        val now = Instant.now().epochSecond.toDouble()
        redisTemplate.opsForZSet().add(scope.redisKey(scopeId), viewerId, now)
    }

    fun getViewerCount(scope: ViewerScope, scopeId: String): Long {
        val cutoff = (Instant.now().epochSecond - VIEWER_TTL_SECONDS).toDouble()
        return redisTemplate.opsForZSet()
            .count(scope.redisKey(scopeId), cutoff, Double.MAX_VALUE) ?: 0L
    }
}
