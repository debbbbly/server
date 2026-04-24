package com.debbly.server.chat

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.chat.model.ChatMessage
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.infra.error.GlobalExceptionHandler
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.mock
import com.debbly.server.pusher.model.SendMessageResult
import com.debbly.server.user.model.UserModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.Instant

class ChatControllerTest {

    private val chatService: ChatService = mock()
    private val authService: AuthService = mock()

    private lateinit var mvc: MockMvc

    private val user = UserModel(
        userId = "u1",
        externalUserId = "ext-u1",
        email = "u1@test.dev",
        username = "alice",
        usernameNormalized = "alice",
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(ChatController(chatService, authService))
            .setCustomArgumentResolvers(FixedExternalUserIdResolver("ext-u1"))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `POST messages returns 200 with SENT result when message is unchanged`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)
        val saved = ChatMessage("msg1", "chat1", "u1", "alice", "hi", Instant.parse("2025-01-01T00:00:00Z"))
        whenever(chatService.sendMessage(eq("chat1"), eq(user), eq("hi")))
            .thenReturn(SendMessageOutcome(SendMessageResult.SENT, saved))

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"hi"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SENT"))
            .andExpect(jsonPath("$.messageId").value("msg1"))
            .andExpect(jsonPath("$.message").value("hi"))
            .andExpect(jsonPath("$.originalMessage").doesNotExist())
    }

    @Test
    fun `POST messages returns 200 with MODERATED result`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)
        val saved = ChatMessage("msg1", "chat1", "u1", "alice", "🚫🚫", Instant.parse("2025-01-01T00:00:00Z"))
        whenever(chatService.sendMessage(eq("chat1"), eq(user), eq("bad words")))
            .thenReturn(SendMessageOutcome(SendMessageResult.MODERATED, saved))

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"bad words"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("MODERATED"))
            .andExpect(jsonPath("$.messageId").value("msg1"))
            .andExpect(jsonPath("$.message").value("🚫🚫"))
    }

    @Test
    fun `POST messages returns 429 when rate limited`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)
        whenever(chatService.sendMessage(eq("chat1"), eq(user), any())).thenReturn(null)

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"hi"}""")
        ).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `POST messages returns 401 when unauthenticated`() {
        whenever(authService.authenticate("ext-u1")).thenThrow(UnauthorizedException())

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"hi"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST messages returns 400 when message is blank`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":""}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST messages returns 400 when message exceeds 1000 chars`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)
        val tooLong = "x".repeat(1001)

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"$tooLong"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST messages returns 403 when service throws ForbiddenException (banned or muted)`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)
        whenever(chatService.sendMessage(eq("chat1"), eq(user), any()))
            .thenThrow(ForbiddenException("You are muted in this chat"))

        mvc.perform(
            post("/chats/chat1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"message":"hi"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("You are muted in this chat"))
    }

    @Test
    fun `PUT mute delegates to service`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)

        mvc.perform(put("/chats/chat1/users/u2/mute"))
            .andExpect(status().isOk)

        verify(chatService).muteUser("chat1", "u1", "u2")
    }

    @Test
    fun `PUT mute returns 403 when service rejects`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)
        whenever(chatService.muteUser("chat1", "u1", "u2"))
            .thenThrow(ForbiddenException("Only the host can mute users"))

        mvc.perform(put("/chats/chat1/users/u2/mute"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE mute delegates to service`() {
        whenever(authService.authenticate("ext-u1")).thenReturn(user)

        mvc.perform(delete("/chats/chat1/users/u2/mute"))
            .andExpect(status().isOk)

        verify(chatService).unmuteUser("chat1", "u1", "u2")
    }

    @Test
    fun `GET messages returns channel history`() {
        val m1 = ChatMessage("m1", "c1", "u1", "alice", "first", Instant.parse("2025-01-01T00:00:01Z"))
        val m2 = ChatMessage("m2", "c1", "u2", "bob", "second", Instant.parse("2025-01-01T00:00:02Z"))
        whenever(chatService.getMessages("c1")).thenReturn(listOf(m1, m2))

        mvc.perform(get("/chats/c1/messages"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.messages[0].messageId").value("m1"))
            .andExpect(jsonPath("$.messages[1].messageId").value("m2"))
    }

    private class FixedExternalUserIdResolver(private val value: String?) : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter): Boolean =
            parameter.getParameterAnnotation(ExternalUserId::class.java) != null

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?
        ): Any? = value
    }
}
