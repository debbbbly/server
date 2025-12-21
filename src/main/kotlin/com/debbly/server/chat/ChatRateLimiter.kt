package com.debbly.server.chat

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import java.time.Duration
import java.util.concurrent.TimeUnit

object ChatRateLimiter {
    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build<String, Bucket>()

    private fun createNewBucket(): Bucket = Bucket.builder()
        .addLimit { limit ->
            limit.capacity(1).refillGreedy(1, Duration.ofSeconds(1))
        }
        .build()

    fun tryConsume(userId: String): Boolean {
        val bucket = cache.get(userId) { createNewBucket() }
        return bucket.tryConsume(1)
    }
}
