package com.debbly.server.chat

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * In-memory, per-JVM rate limiter. With multiple replicas each instance holds its own buckets,
 * so the effective global limit is roughly N × [MESSAGES_PER_SECOND]. Acceptable for chat; if we
 * ever need a hard global cap, back this with Redis.
 */
@Component
class ChatRateLimiter {

    companion object {
        private const val MESSAGES_PER_SECOND = 1L
        private const val MAX_TRACKED_USERS = 10_000L
        private const val INACTIVE_USER_EVICTION_MINUTES = 10L
    }

    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(INACTIVE_USER_EVICTION_MINUTES, TimeUnit.MINUTES)
        .maximumSize(MAX_TRACKED_USERS)
        .build<String, Bucket>()

    private fun createNewBucket(): Bucket = Bucket.builder()
        .addLimit { limit ->
            limit.capacity(MESSAGES_PER_SECOND).refillGreedy(MESSAGES_PER_SECOND, Duration.ofSeconds(1))
        }
        .build()

    fun tryConsume(userId: String): Boolean {
        val bucket = cache.get(userId) { createNewBucket() }
        return bucket.tryConsume(1)
    }
}
