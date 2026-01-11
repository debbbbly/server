package com.debbly.server.claim

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.TimeUnit

object ClaimRateLimiter {
    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(2, TimeUnit.DAYS)
        .maximumSize(10_000)
        .build<String, Bucket>()

    private fun createNewBucket(): Bucket = Bucket.builder()
        .addLimit { limit ->
            limit.capacity(10).refillGreedy(10, Duration.ofDays(1))
        }
        .build()

    fun tryConsume(userId: String): Boolean {
        val bucket = cache.get(userId) { createNewBucket() }
        return bucket.tryConsume(1)
    }
}
