package com.debbly.server.match

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.TimeUnit

object MatchQueueRateLimiter {
    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .maximumSize(10_000)
        .build<String, Bucket>()

    private fun createNewBucket(): Bucket = Bucket.builder()
        .addLimit { limit ->
            limit.capacity(20).refillGreedy(20, Duration.ofMinutes(10))
        }
        .build()

    fun tryConsume(userId: String): Boolean {
        val bucket = cache.get(userId) { createNewBucket() }
        return bucket.tryConsume(1)
    }
}
