package com.debbly.server.user

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.TimeUnit

object ReportRateLimiter {
    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .maximumSize(10_000)
        .build<String, Bucket>()

    private fun createNewBucket(): Bucket = Bucket.builder()
        .addLimit { limit ->
            limit.capacity(1).refillGreedy(1, Duration.ofMinutes(10))
        }
        .build()

    fun tryConsume(key: String): Boolean {
        val bucket = cache.get(key) { createNewBucket() }
        return bucket.tryConsume(1)
    }
}
