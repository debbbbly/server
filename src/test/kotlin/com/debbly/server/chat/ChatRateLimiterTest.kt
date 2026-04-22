package com.debbly.server.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ChatRateLimiterTest {

    private val limiter = ChatRateLimiter()

    @Test
    fun `first call for a user is allowed`() {
        assertTrue(limiter.tryConsume(newUserId()))
    }

    @Test
    fun `second immediate call for same user is denied`() {
        val userId = newUserId()
        limiter.tryConsume(userId)
        assertFalse(limiter.tryConsume(userId))
    }

    @Test
    fun `different users do not share buckets`() {
        val u1 = newUserId()
        val u2 = newUserId()
        assertTrue(limiter.tryConsume(u1))
        assertTrue(limiter.tryConsume(u2))
    }

    @Test
    fun `bucket refills after one second`() {
        val userId = newUserId()
        assertTrue(limiter.tryConsume(userId))
        assertFalse(limiter.tryConsume(userId))

        Thread.sleep(1100)

        assertTrue(limiter.tryConsume(userId))
    }

    @Test
    fun `separate limiter instances do not share state`() {
        val userId = newUserId()
        val other = ChatRateLimiter()
        limiter.tryConsume(userId)
        assertTrue(other.tryConsume(userId))
    }

    private fun newUserId(): String = UUID.randomUUID().toString()
}
