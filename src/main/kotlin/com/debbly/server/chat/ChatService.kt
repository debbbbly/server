package com.debbly.server.chat

import com.debbly.server.IdService
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.chat.model.ChatMessage
import com.debbly.server.event.repository.EventCachedRepository
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.moderation.ModerationApiService
import com.debbly.server.pusher.model.ChannelMessageResponse
import com.debbly.server.pusher.model.PusherEventName.CHAT_EVENT
import com.debbly.server.pusher.model.PusherMessage
import com.debbly.server.pusher.model.PusherMessageType.CHAT_MESSAGE
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.user.model.UserModel
import org.springframework.stereotype.Service
import java.time.Clock

data class SendMessageOutcome(
    val message: ChatMessage,
    val wasModerated: Boolean
)

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val idService: IdService,
    private val clock: Clock,
    private val rateLimiter: ChatRateLimiter,
    private val moderationApiService: ModerationApiService,
    private val pusherService: PusherService,
    private val stageCachedRepository: StageCachedRepository,
    private val eventCachedRepository: EventCachedRepository
) {

    fun sendMessage(chatId: String, user: UserModel, message: String): SendMessageOutcome? {
        if (!checkCanSend(chatId, user)) return null

        val moderation = moderationApiService.moderateChatMessage(message)
        val saved = saveMessage(
            channelId = chatId,
            userId = user.userId,
            username = user.username,
            message = moderation.message
        )
        broadcastMessage(chatId, saved)

        return SendMessageOutcome(saved, moderation.wasModerated)
    }

    private fun checkCanSend(chatId: String, user: UserModel): Boolean {
        if (user.banned) throw ForbiddenException("Your account is limited")
        if (!rateLimiter.tryConsume(user.userId)) return false
        if (chatRepository.isMuted(chatId, user.userId)) throw ForbiddenException("You are muted in this chat")
        return true
    }

    private fun broadcastMessage(chatId: String, saved: ChatMessage) {
        val payload = ChannelMessageResponse(
            messageId = saved.messageId,
            userId = saved.userId,
            username = saved.username,
            message = saved.message,
            timestamp = saved.timestamp.toString()
        )
        pusherService.sendChannelMessage(chatId, CHAT_EVENT, PusherMessage.message(CHAT_MESSAGE, payload))
    }

    fun muteUser(chatId: String, requesterId: String, targetUserId: String) {
        requireChatHost(chatId, requesterId)
        chatRepository.muteUser(chatId, targetUserId)
    }

    fun unmuteUser(chatId: String, requesterId: String, targetUserId: String) {
        requireChatHost(chatId, requesterId)
        chatRepository.unmuteUser(chatId, targetUserId)
    }

    fun getMessages(channelId: String): List<ChatMessage> =
        chatRepository.findByChannelIdOrderByTimestampDesc(channelId).asReversed()

    private fun saveMessage(
        channelId: String,
        userId: String,
        username: String,
        message: String
    ): ChatMessage {
        val chatMessage = ChatMessage(
            messageId = idService.getId(),
            chatId = channelId,
            userId = userId,
            username = username,
            message = message,
            timestamp = clock.instant()
        )
        chatRepository.save(chatMessage)
        return chatMessage
    }

    private fun requireChatHost(chatId: String, userId: String) {
        val isStageHost = stageCachedRepository.findById(chatId)
            ?.hosts?.any { it.userId == userId } == true
        if (isStageHost) return

        val isEventHost = eventCachedRepository.findById(chatId)?.hostUserId == userId
        if (!isEventHost) throw ForbiddenException("Only the host can mute users")
    }
}
