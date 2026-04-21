package com.debbly.server.chat

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.moderation.ModerationApiService
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.event.repository.EventCachedRepository
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.pusher.model.ChannelHistoryResponse
import com.debbly.server.pusher.model.ChannelMessageResponse
import com.debbly.server.pusher.model.PusherMessageType.CHAT_MESSAGE
import com.debbly.server.pusher.model.PusherEventName.CHAT_EVENT
import com.debbly.server.pusher.model.PusherMessage
import com.debbly.server.pusher.model.SendMessageRequest
import com.debbly.server.pusher.model.SendMessageResponse
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.stage.repository.StageCachedRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/chats")
class ChatController(
    private val pusherService: PusherService,
    private val chatService: ChatService,
    private val chatRepository: ChatRepository,
    private val authService: AuthService,
    private val moderationApiService: ModerationApiService,
    private val stageCachedRepository: StageCachedRepository,
    private val eventCachedRepository: EventCachedRepository
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
            if (chatRepository.isMuted(chatId, user.userId)) throw ForbiddenException("You are muted in this chat")

            // Rate limiting: 1 message per second per user
            if (!ChatRateLimiter.tryConsume(user.userId)) {
                logger.warn("Rate limit exceeded for user ${user.userId} in chat $chatId")
                return ResponseEntity.status(429).build()
            }

            val moderationResult = moderationApiService.moderateChatMessage(request.message)

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

    @PutMapping("/{chatId}/users/{userId}/mute")
    fun muteUser(
        @PathVariable chatId: String,
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Unit> {
        val requester = authService.authenticate(externalUserId)
        requireChatHost(chatId, requester.userId)
        chatRepository.muteUser(chatId, userId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{chatId}/users/{userId}/mute")
    fun unmuteUser(
        @PathVariable chatId: String,
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Unit> {
        val requester = authService.authenticate(externalUserId)
        requireChatHost(chatId, requester.userId)
        chatRepository.unmuteUser(chatId, userId)
        return ResponseEntity.ok().build()
    }

    private fun requireChatHost(chatId: String, userId: String) {
        val isStageHost = stageCachedRepository.findById(chatId)
            ?.hosts?.any { it.userId == userId } == true
        val isEventHost = !isStageHost && eventCachedRepository.findById(chatId)
            ?.hostUserId == userId
        if (!isStageHost && !isEventHost) throw ForbiddenException("Only the host can mute users")
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