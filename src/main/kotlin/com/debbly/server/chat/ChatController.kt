package com.debbly.server.chat

import com.debbly.server.ai.OpenAiService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.pusher.model.ChannelHistoryResponse
import com.debbly.server.pusher.model.ChannelMessageResponse
import com.debbly.server.pusher.model.PusherMessageType.CHAT_MESSAGE
import com.debbly.server.pusher.model.PusherEventName.CHAT_EVENT
import com.debbly.server.pusher.model.PusherMessage
import com.debbly.server.pusher.model.SendMessageRequest
import com.debbly.server.pusher.model.SendMessageResponse
import com.debbly.server.pusher.service.PusherService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/chats")
class ChatController(
    private val pusherService: PusherService,
    private val chatService: ChatService,
    private val authService: AuthService,
    private val openAIService: OpenAiService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{chatId}/messages")
    fun sendMessage(
        @PathVariable chatId: String,
        @Valid @RequestBody request: SendMessageRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<SendMessageResponse> {
        try {
            val user = authService.authenticate(externalUserId)
            if (user.banned) throw ForbiddenException("Your account is limited")

            // Rate limiting: 1 message per second per user
            if (!ChatRateLimiter.tryConsume(user.userId)) {
                logger.warn("Rate limit exceeded for user ${user.userId} in chat $chatId")
                return ResponseEntity.status(429).build()
            }

            val moderationResult = openAIService.moderateChatMessage(request.message)

            val channelMessage = chatService.saveMessage(
                channelId = chatId,
                userId = user.userId,
                username = user.username,
                message = moderationResult.message
            )

            val pusherMessage = ChannelMessageResponse(
                messageId = channelMessage.messageId,
                userId = channelMessage.userId,
                username = channelMessage.username,
                message = channelMessage.message,
                timestamp = channelMessage.timestamp.toString()
            )

            val message = PusherMessage.message(CHAT_MESSAGE, pusherMessage)
            pusherService.sendChannelMessage(chatId, CHAT_EVENT, message)

            val response = SendMessageResponse(
                messageId = channelMessage.messageId,
                message = channelMessage.message,
                moderated = moderationResult.wasModerated,
                // originalMessage = if (moderationResult.wasModerated) request.message else null
            )

            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Failed to send message to channel $chatId: ${e.message}", e)
            return ResponseEntity.status(401).build()
        }
    }

    @GetMapping("/{chatId}/messages")
    fun getMessages(
        @PathVariable chatId: String,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<ChannelHistoryResponse> {
        try {
            val messages = chatService.getMessages(chatId, limit)
            val responses = messages.map { message ->
                ChannelMessageResponse(
                    messageId = message.messageId,
                    userId = message.userId,
                    username = message.username,
                    message = message.message,
                    timestamp = message.timestamp.toString()
                )
            }

            return ResponseEntity.ok(ChannelHistoryResponse(responses))
        } catch (e: Exception) {
            logger.error("Failed to get messages from channel $chatId: ${e.message}", e)
            return ResponseEntity.internalServerError().build()
        }
    }
}