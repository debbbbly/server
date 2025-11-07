package com.debbly.server.chat

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.pusher.dto.ChannelHistoryResponse
import com.debbly.server.pusher.dto.ChannelMessageResponse
import com.debbly.server.pusher.dto.SendMessageRequest
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
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{chatId}/messages")
    fun sendMessage(
        @PathVariable chatId: String,
        @Valid @RequestBody request: SendMessageRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<ChannelMessageResponse> {
        try {
            val user = authService.authenticate(externalUserId)

            val channelMessage = chatService.saveMessage(
                channelId = chatId,
                userId = user.userId,
                username = user.username,
                message = request.message
            )

            val response = ChannelMessageResponse(
                messageId = channelMessage.messageId,
                userId = channelMessage.userId,
                username = channelMessage.username,
                message = channelMessage.message,
                timestamp = channelMessage.timestamp
            )

            pusherService.sendChannelMessage(chatId, response)

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
                    timestamp = message.timestamp
                )
            }

            return ResponseEntity.ok(ChannelHistoryResponse(responses))
        } catch (e: Exception) {
            logger.error("Failed to get messages from channel $chatId: ${e.message}", e)
            return ResponseEntity.internalServerError().build()
        }
    }
}