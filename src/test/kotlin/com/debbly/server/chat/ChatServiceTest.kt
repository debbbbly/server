package com.debbly.server.chat

import com.debbly.server.IdService
import com.debbly.server.chat.model.ChatMessage
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.event.model.EventStatus
import com.debbly.server.event.repository.EventCachedRepository
import com.debbly.server.event.repository.EventEntity
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.mock
import com.debbly.server.moderation.ChatModerationResult
import com.debbly.server.moderation.ModerationApiService
import com.debbly.server.pusher.model.ChannelMessageResponse
import com.debbly.server.pusher.model.PusherEventName.CHAT_EVENT
import com.debbly.server.pusher.model.PusherMessage
import com.debbly.server.pusher.model.PusherMessageType.CHAT_MESSAGE
import com.debbly.server.pusher.model.SendMessageResult
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.StageType
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.stage.repository.entities.StageVisibility
import com.debbly.server.user.model.UserModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ChatServiceTest {

    private val chatRepository: ChatRepository = mock()
    private val idService: IdService = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val rateLimiter: ChatRateLimiter = mock()
    private val moderationApiService: ModerationApiService = mock()
    private val pusherService: PusherService = mock()
    private val stageCachedRepository: StageCachedRepository = mock()
    private val eventCachedRepository: EventCachedRepository = mock()

    private val service = ChatService(
        chatRepository = chatRepository,
        idService = idService,
        clock = clock,
        rateLimiter = rateLimiter,
        moderationApiService = moderationApiService,
        pusherService = pusherService,
        stageCachedRepository = stageCachedRepository,
        eventCachedRepository = eventCachedRepository
    )

    private val user = userModel("u1", "alice", banned = false)

    @BeforeEach
    fun setUp() {
        whenever(rateLimiter.tryConsume(any())).thenReturn(true)
        whenever(idService.getId()).thenReturn("msg1")
        whenever(moderationApiService.moderateChatMessage(any()))
            .thenAnswer { ChatModerationResult(message = it.arguments[0] as String, wasModerated = false) }
    }

    @Test
    fun `sendMessage saves moderated text, broadcasts via pusher, returns outcome`() {
        whenever(moderationApiService.moderateChatMessage("hello"))
            .thenReturn(ChatModerationResult(message = "🚫🚫", wasModerated = true))

        val outcome = service.sendMessage("chat1", user, "hello")!!

        assertEquals("msg1", outcome.message.messageId)
        assertEquals("🚫🚫", outcome.message.message)
        assertEquals(SendMessageResult.MODERATED, outcome.result)
        verify(chatRepository).save(outcome.message)

        val pusherCaptor = argumentCaptor<PusherMessage>()
        verify(pusherService).sendChannelMessage(eq("chat1"), eq(CHAT_EVENT), pusherCaptor.capture())
        val envelope = pusherCaptor.firstValue
        assertEquals(CHAT_MESSAGE, envelope.type)
        val payload = envelope.data as ChannelMessageResponse
        assertEquals("msg1", payload.messageId)
        assertEquals("u1", payload.userId)
        assertEquals("alice", payload.username)
        assertEquals("🚫🚫", payload.message)
        assertEquals(outcome.message.timestamp.toString(), payload.timestamp)
    }

    @Test
    fun `sendMessage with clean message returns SENT and null originalMessage`() {
        val outcome = service.sendMessage("chat1", user, "hello")!!

        assertEquals("hello", outcome.message.message)
        assertEquals(SendMessageResult.SENT, outcome.result)
    }

    @Test
    fun `sendMessage returns null when rate limit exceeded, no save, no moderation, no broadcast`() {
        whenever(rateLimiter.tryConsume("u1")).thenReturn(false)

        val outcome = service.sendMessage("chat1", user, "hi")

        assertNull(outcome)
        verify(moderationApiService, never()).moderateChatMessage(any())
        verify(chatRepository, never()).save(any())
        verify(pusherService, never()).sendChannelMessage(any(), any(), any())
    }

    @Test
    fun `sendMessage throws ForbiddenException when user is banned, skips everything else`() {
        val banned = user.copy(banned = true)

        assertThrows(ForbiddenException::class.java) {
            service.sendMessage("chat1", banned, "hi")
        }
        verify(rateLimiter, never()).tryConsume(any())
        verify(chatRepository, never()).save(any())
    }

    @Test
    fun `sendMessage throws ForbiddenException when user is muted, after rate-limit check`() {
        whenever(chatRepository.isMuted("chat1", "u1")).thenReturn(true)

        assertThrows(ForbiddenException::class.java) {
            service.sendMessage("chat1", user, "hi")
        }
        verify(rateLimiter).tryConsume("u1")
        verify(moderationApiService, never()).moderateChatMessage(any())
        verify(chatRepository, never()).save(any())
    }

    @Test
    fun `muteUser requires stage-host, then writes to repo`() {
        whenever(stageCachedRepository.findById("chat1"))
            .thenReturn(stageModel("chat1", hostIds = listOf("host1")))

        service.muteUser("chat1", requesterId = "host1", targetUserId = "u2")

        verify(chatRepository).muteUser("chat1", "u2")
    }

    @Test
    fun `muteUser falls back to event-host when not a stage`() {
        whenever(stageCachedRepository.findById("chat1")).thenReturn(null)
        whenever(eventCachedRepository.findById("chat1"))
            .thenReturn(eventEntity("chat1", hostUserId = "host1"))

        service.muteUser("chat1", requesterId = "host1", targetUserId = "u2")

        verify(chatRepository).muteUser("chat1", "u2")
    }

    @Test
    fun `muteUser rejects non-host`() {
        whenever(stageCachedRepository.findById("chat1")).thenReturn(null)
        whenever(eventCachedRepository.findById("chat1")).thenReturn(null)

        assertThrows(ForbiddenException::class.java) {
            service.muteUser("chat1", requesterId = "intruder", targetUserId = "u2")
        }
    }

    @Test
    fun `unmuteUser requires host and removes from repo`() {
        whenever(stageCachedRepository.findById("chat1"))
            .thenReturn(stageModel("chat1", hostIds = listOf("host1")))

        service.unmuteUser("chat1", requesterId = "host1", targetUserId = "u2")

        verify(chatRepository).unmuteUser("chat1", "u2")
    }

    @Test
    fun `getMessages returns repository data reversed (ascending by time)`() {
        val m1 = ChatMessage("m1", "c1", "u1", "a", "first", Instant.parse("2025-01-01T00:00:01Z"))
        val m2 = ChatMessage("m2", "c1", "u2", "b", "second", Instant.parse("2025-01-01T00:00:02Z"))
        whenever(chatRepository.findByChannelIdOrderByTimestampDesc("c1")).thenReturn(listOf(m2, m1))

        val result = service.getMessages("c1")

        assertEquals(listOf(m1, m2), result)
    }

    @Test
    fun `getMessages returns empty list when repository has no data`() {
        whenever(chatRepository.findByChannelIdOrderByTimestampDesc("c1")).thenReturn(emptyList())

        assertEquals(emptyList<ChatMessage>(), service.getMessages("c1"))
    }

    private fun userModel(userId: String, username: String, banned: Boolean) = UserModel(
        userId = userId,
        externalUserId = "ext-$userId",
        email = "$userId@test.dev",
        username = username,
        usernameNormalized = username.lowercase(),
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        banned = banned
    )

    private fun stageModel(stageId: String, hostIds: List<String>) = StageModel(
        stageId = stageId,
        type = StageType.SOLO,
        title = null,
        claimId = null,
        hosts = hostIds.map { StageModel.StageHostModel(userId = it, stance = null) },
        status = StageStatus.OPEN,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        openedAt = null,
        closedAt = null,
        visibility = StageVisibility.PUBLIC
    )

    private fun eventEntity(eventId: String, hostUserId: String) = EventEntity(
        eventId = eventId,
        claimId = "claim1",
        hostUserId = hostUserId,
        hostStance = ClaimStance.FOR,
        startTime = Instant.parse("2025-01-01T00:00:00Z"),
        status = EventStatus.SCHEDULED,
        description = null,
        bannerImageUrl = null,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z")
    )
}
