package com.debbly.server.chat

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.pusher.model.ChannelHistoryResponse
import com.debbly.server.pusher.model.ChannelMessageResponse
import com.debbly.server.pusher.model.SendMessageRequest
import com.debbly.server.pusher.model.SendMessageResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/chats")
class ChatController(
    private val chatService: ChatService,
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{chatId}/messages")
    fun sendMessage(
        @PathVariable chatId: String,
        @Valid @RequestBody request: SendMessageRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<SendMessageResponse> {
        val user = authService.authenticate(externalUserId)

        val outcome = chatService.sendMessage(chatId, user, request.message)
            ?: run {
                logger.debug("Rate limit exceeded for user {} in chat {}", user.userId, chatId)
                return ResponseEntity.status(TOO_MANY_REQUESTS).build()
            }

        return ResponseEntity.ok(
            SendMessageResponse(
                result = outcome.result,
                messageId = outcome.message.messageId,
                message = outcome.message.message,
            )
        )
    }

    @PutMapping("/{chatId}/users/{userId}/mute")
    fun muteUser(
        @PathVariable chatId: String,
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Unit> {
        val requester = authService.authenticate(externalUserId)
        chatService.muteUser(chatId, requester.userId, userId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{chatId}/users/{userId}/mute")
    fun unmuteUser(
        @PathVariable chatId: String,
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Unit> {
        val requester = authService.authenticate(externalUserId)
        chatService.unmuteUser(chatId, requester.userId, userId)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{chatId}/messages")
    fun getMessages(@PathVariable chatId: String): ResponseEntity<ChannelHistoryResponse> {
        val responses = chatService.getMessages(chatId).map { message ->
            ChannelMessageResponse(
                messageId = message.messageId,
                userId = message.userId,
                username = message.username,
                message = message.message,
                timestamp = message.timestamp.toString()
            )
        }
        return ResponseEntity.ok(ChannelHistoryResponse(responses))
    }
}
