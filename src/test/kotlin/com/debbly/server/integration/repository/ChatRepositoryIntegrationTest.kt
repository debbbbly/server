package com.debbly.server.integration.repository

import com.debbly.server.chat.model.ChatMessage
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisTemplate
import java.time.Instant
import java.util.concurrent.TimeUnit

class ChatRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: ChatRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

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
    fun `read from empty chat returns empty list`() {
        assertEquals(emptyList<ChatMessage>(), repo.findByChannelIdOrderByTimestampDesc("never-used"))
    }

    @Test
    fun `save trims to the last MAX_MESSAGES_PER_CHAT entries`() {
        val base = Instant.parse("2025-01-01T00:00:00Z")
        repeat(ChatRepository.MAX_MESSAGES_PER_CHAT + 5) { i ->
            repo.save(ChatMessage("m$i", "c1", "u1", "alice", "msg-$i", base.plusSeconds(i.toLong())))
        }

        val stored = repo.findByChannelIdOrderByTimestampDesc("c1")
        assertEquals(ChatRepository.MAX_MESSAGES_PER_CHAT, stored.size)
        // Oldest messages dropped, newest kept (descending order).
        assertEquals("m${ChatRepository.MAX_MESSAGES_PER_CHAT + 4}", stored.first().messageId)
    }

    @Test
    fun `save sets a TTL on the messages key`() {
        val m = ChatMessage("m1", "c1", "u1", "alice", "hello", Instant.parse("2025-01-01T00:00:00Z"))
        repo.save(m)

        val ttlSeconds = redisTemplate.getExpire("chat:c1:messages", TimeUnit.SECONDS)
        assertNotNull(ttlSeconds)
        // 15 minutes = 900s; allow some slack for execution time.
        assertTrue(ttlSeconds in 800..900) { "expected TTL close to 15 min, got $ttlSeconds" }
    }

    @Test
    fun `mute and unmute user`() {
        assertFalse(repo.isMuted("c1", "u1"))
        repo.muteUser("c1", "u1")
        assertTrue(repo.isMuted("c1", "u1"))
        repo.unmuteUser("c1", "u1")
        assertFalse(repo.isMuted("c1", "u1"))
    }

    @Test
    fun `messages and mutes are scoped per chat`() {
        val base = Instant.parse("2025-01-01T00:00:00Z")
        repo.save(ChatMessage("a1", "roomA", "u1", "alice", "hello-A", base))
        repo.save(ChatMessage("b1", "roomB", "u1", "alice", "hello-B", base.plusSeconds(1)))
        repo.muteUser("roomA", "u2")

        val inA = repo.findByChannelIdOrderByTimestampDesc("roomA")
        val inB = repo.findByChannelIdOrderByTimestampDesc("roomB")
        assertEquals(listOf("a1"), inA.map { it.messageId })
        assertEquals(listOf("b1"), inB.map { it.messageId })

        assertTrue(repo.isMuted("roomA", "u2"))
        assertFalse(repo.isMuted("roomB", "u2"))
    }

    @Test
    fun `findByChannelId skips non-deserializable entries instead of failing`() {
        val m = ChatMessage("m1", "c1", "u1", "alice", "hello", Instant.parse("2025-01-01T00:00:00Z"))
        repo.save(m)
        redisTemplate.opsForZSet().add("chat:c1:messages", "not-json", 999_999_999.0)

        val result = repo.findByChannelIdOrderByTimestampDesc("c1")
        assertEquals(listOf("m1"), result.map { it.messageId })
    }
}
