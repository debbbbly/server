package com.debbly.server.pusher.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class SendMessageRequest(
    @field:NotBlank(message = "Message cannot be blank")
    @field:Size(max = 1000, message = "Message cannot exceed 1000 characters")
    val message: String
)

data class ChannelMessageResponse(
    val messageId: String,
    val userId: String,
    val username: String,
    val message: String,
    val timestamp: Instant
)

data class ChannelHistoryResponse(
    val messages: List<ChannelMessageResponse>
)

data class PresenceUserInfo(
    val userId: String?,
    val username: String,
    val avatarUrl: String?,
    val isRegistered: Boolean
)
