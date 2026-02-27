package com.debbly.server.chat.repository

import com.debbly.server.chat.model.ChatMessage
import com.fasterxml.jackson.databind.ObjectMapper
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
    }

    private fun getChatKey(chatId: String): String = "$KEY_PREFIX:$chatId:messages"

    fun save(message: ChatMessage) {
        val key = getChatKey(message.chatId)
        val messageJson = objectMapper.writeValueAsString(message)
        val score = message.timestamp.toEpochMilli().toDouble()

        redisTemplate.opsForZSet().add(key, messageJson, score)
        redisTemplate.opsForZSet().removeRange(key, 0, -11) // keep last 10
        redisTemplate.expire(key, MESSAGE_TTL_MINUTES, TimeUnit.MINUTES)
    }

    private fun getMutedKey(chatId: String): String = "$KEY_PREFIX:$chatId:muted"

    fun muteUser(chatId: String, userId: String) {
        redisTemplate.opsForSet().add(getMutedKey(chatId), userId)
    }

    fun unmuteUser(chatId: String, userId: String) {
        redisTemplate.opsForSet().remove(getMutedKey(chatId), userId)
    }

    fun isMuted(chatId: String, userId: String): Boolean =
        redisTemplate.opsForSet().isMember(getMutedKey(chatId), userId) == true

    fun findByChannelIdOrderByTimestampDesc(channelId: String, limit: Int = 100): List<ChatMessage> {
        val key = getChatKey(channelId)

        val messageJsons = redisTemplate.opsForZSet()
            .reverseRange(key, 0, (limit - 1).toLong()) ?: emptySet()

        return messageJsons.mapNotNull { json ->
            try {
                objectMapper.readValue(json, ChatMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
