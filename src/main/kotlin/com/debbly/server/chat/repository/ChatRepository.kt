package com.debbly.server.chat.repository

import com.debbly.server.chat.model.ChatMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class ChatRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private const val KEY_PREFIX = "chat"
        private const val MESSAGE_TTL_MINUTES = 15L
        const val MAX_MESSAGES_PER_CHAT = 10
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun getChatKey(chatId: String): String = "$KEY_PREFIX:$chatId:messages"
    private fun getMutedKey(chatId: String): String = "$KEY_PREFIX:$chatId:muted"

    fun save(message: ChatMessage) {
        val key = getChatKey(message.chatId)
        val messageJson = objectMapper.writeValueAsString(message)
        val score = message.timestamp.toEpochMilli().toDouble()

        redisTemplate.opsForZSet().add(key, messageJson, score)
        redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_MESSAGES_PER_CHAT + 1).toLong())
        redisTemplate.expire(key, MESSAGE_TTL_MINUTES, TimeUnit.MINUTES)
    }

    fun muteUser(chatId: String, userId: String) {
        redisTemplate.opsForSet().add(getMutedKey(chatId), userId)
    }

    fun unmuteUser(chatId: String, userId: String) {
        redisTemplate.opsForSet().remove(getMutedKey(chatId), userId)
    }

    fun isMuted(chatId: String, userId: String): Boolean =
        redisTemplate.opsForSet().isMember(getMutedKey(chatId), userId) == true

    fun findByChannelIdOrderByTimestampDesc(channelId: String): List<ChatMessage> {
        val key = getChatKey(channelId)

        val messageJsons = redisTemplate.opsForZSet()
            .reverseRange(key, 0, (MAX_MESSAGES_PER_CHAT - 1).toLong()) ?: emptySet()

        return messageJsons.mapNotNull { json ->
            try {
                objectMapper.readValue(json, ChatMessage::class.java)
            } catch (e: Exception) {
                logger.warn("Dropping chat message that failed to deserialize in chat {}: {}", channelId, e.message)
                null
            }
        }
    }
}
