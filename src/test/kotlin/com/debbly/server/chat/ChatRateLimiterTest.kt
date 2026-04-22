package com.debbly.server.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ChatRateLimiterTest {

    @Test
    fun `first call for a user is allowed`() {
        val userId = UUID.randomUUID().toString()
        assertTrue(ChatRateLimiter.tryConsume(userId))
    }

    @Test
    fun `second immediate call for same user is denied`() {
        val userId = UUID.randomUUID().toString()
        ChatRateLimiter.tryConsume(userId)
        assertFalse(ChatRateLimiter.tryConsume(userId))
    }
}
