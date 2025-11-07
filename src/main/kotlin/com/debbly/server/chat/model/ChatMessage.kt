package com.debbly.server.chat.model

import java.time.Instant

data class ChatMessage(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val username: String,
    val message: String,
    val timestamp: Instant
)