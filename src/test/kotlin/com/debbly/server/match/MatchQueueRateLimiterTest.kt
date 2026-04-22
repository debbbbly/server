package com.debbly.server.match

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MatchQueueRateLimiterTest {

    @Test
    fun `allows up to capacity calls`() {
        val userId = UUID.randomUUID().toString()
        repeat(20) { assertTrue(MatchQueueRateLimiter.tryConsume(userId)) }
    }

    @Test
    fun `denies after capacity exhausted`() {
        val userId = UUID.randomUUID().toString()
        repeat(20) { MatchQueueRateLimiter.tryConsume(userId) }
        assertFalse(MatchQueueRateLimiter.tryConsume(userId))
    }
}
