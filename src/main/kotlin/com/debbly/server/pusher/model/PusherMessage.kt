package com.debbly.server.pusher.model

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Unified envelope for all Pusher messages.
 * Provides consistent structure across all channels and event types.
 */
data class PusherMessage(
    val type: PusherMessageType,
    val data: Any,
    val timestamp: String
) {
    companion object {
        fun message(type: PusherMessageType, data: Any): PusherMessage {
            return PusherMessage(
                type = type,
                data = data,
                timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            )
        }
    }
}

/**
 * Message types for all Pusher events
 */
enum class PusherMessageType {
    // Chat messages
    CHAT_MESSAGE,

    // Match notifications
    MATCH_FOUND,
    MATCH_ACCEPTED,
    MATCH_ACCEPTED_ALL,
    MATCH_EXPIRED,
    MATCH_FAILED,
    MATCH_STILL_WAITING,
    MATCH_QUEUE_REMOVED,

    // Stage notifications
    STAGE_OPEN,
    STAGE_CLOSED,

    // Queue notifications
    QUEUE_UPDATE
}

enum class PusherEventName {

    STAGE_EVENT,
    CHAT_EVENT,
    MATCH_EVENT,
    QUEUE_EVENT
}