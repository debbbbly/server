package com.debbly.server.chat

import com.debbly.server.IdService
import com.debbly.server.chat.model.ChatMessage
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.mock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ChatServiceTest {

    private val chatRepository: ChatRepository = mock()
    private val idService: IdService = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val service = ChatService(chatRepository, idService, clock)

    @Test
    fun `saveMessage returns message with generated id and timestamp`() {
        whenever(idService.getId()).thenReturn("msg1")

        val result = service.saveMessage("chan1", "u1", "alice", "hello")

        assertEquals("msg1", result.messageId)
        assertEquals("chan1", result.chatId)
        assertEquals("u1", result.userId)
        assertEquals("alice", result.username)
        assertEquals("hello", result.message)
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), result.timestamp)
        verify(chatRepository).save(result)
    }

    @Test
    fun `getMessages returns repository data reversed`() {
        val m1 = ChatMessage("m1", "c1", "u1", "a", "first", Instant.parse("2025-01-01T00:00:01Z"))
        val m2 = ChatMessage("m2", "c1", "u2", "b", "second", Instant.parse("2025-01-01T00:00:02Z"))
        whenever(chatRepository.findByChannelIdOrderByTimestampDesc("c1", 100)).thenReturn(listOf(m2, m1))

        val result = service.getMessages("c1")

        assertEquals(listOf(m1, m2), result)
    }
}
