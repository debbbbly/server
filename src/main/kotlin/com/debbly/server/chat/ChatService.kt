package com.debbly.server.chat

import com.debbly.server.IdService
import com.debbly.server.chat.repository.ChatRepository
import com.debbly.server.chat.model.ChatMessage
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val idService: IdService,
    private val clock: Clock
) {

    companion object {
        private const val LIMIT_MESSAGES_PER_CHANNEL = 100
    }

    fun saveMessage(
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
            timestamp = Instant.now(clock)
        )

        chatRepository.save(chatMessage)

        return chatMessage
    }

    fun getMessages(channelId: String, limit: Int = LIMIT_MESSAGES_PER_CHANNEL): List<ChatMessage> {
        return chatRepository.findByChannelIdOrderByTimestampDesc(channelId, limit)
            .reversed()
    }
}