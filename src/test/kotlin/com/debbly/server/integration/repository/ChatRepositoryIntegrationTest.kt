package com.debbly.server.integration.repository

import com.debbly.server.chat.model.ChatMessage
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ChatRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: ChatRepository

    @BeforeEach
    fun setup() {
        flushRedis()
    }

    @Test
    fun `save then read-back preserves messages in reverse time order`() {
        val base = Instant.parse("2025-01-01T00:00:00Z")
        val m1 = ChatMessage("m1", "c1", "u1", "alice", "hello", base)
        val m2 = ChatMessage("m2", "c1", "u2", "bob", "world", base.plusSeconds(1))

        repo.save(m1)
        repo.save(m2)

        val result = repo.findByChannelIdOrderByTimestampDesc("c1")
        assertEquals(listOf("m2", "m1"), result.map { it.messageId })
    }

    @Test
    fun `mute and unmute user`() {
        assertFalse(repo.isMuted("c1", "u1"))
        repo.muteUser("c1", "u1")
        assertTrue(repo.isMuted("c1", "u1"))
        repo.unmuteUser("c1", "u1")
        assertFalse(repo.isMuted("c1", "u1"))
    }
}
