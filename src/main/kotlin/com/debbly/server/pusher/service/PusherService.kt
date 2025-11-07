package com.debbly.server.pusher.service

import com.pusher.rest.Pusher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PusherService(
    private val pusher: Pusher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val STAGE_CHANNEL_PREFIX = "public-stage-"
        const val STAGE_HOSTS_CHANNEL_PREFIX = "presence-stage-hosts-"
        const val GLOBAL_CHANNEL = "public-global"
        const val USER_CHANNEL_PREFIX = "private-user-"

        const val EVENT_CHAT_MESSAGE = "chat-message"
        const val EVENT_SYSTEM = "system"
        const val EVENT_NOTIFICATION = "notification"
        const val EVENT_PRESENCE_UPDATE = "presence-update"
    }

    /**
     * Send a message to any channel (stage or global)
     */
    fun sendChannelMessage(channelId: String, data: Any) {
        val channelName = if (channelId == "global") {
            GLOBAL_CHANNEL
        } else {
            "$STAGE_CHANNEL_PREFIX$channelId"
        }
        triggerEvent(channelName, EVENT_CHAT_MESSAGE, data)
    }

    /**
     * Send presence update for stage hosts
     */
    fun sendStageHostsPresenceUpdate(stageId: String, data: Any) {
        val channelName = "$STAGE_HOSTS_CHANNEL_PREFIX$stageId"
        triggerEvent(channelName, EVENT_PRESENCE_UPDATE, data)
    }

    /**
     * Send a system message to a stage channel
     */
    fun sendStageSystemMessage(stageId: String, data: Any) {
        val channelName = "$STAGE_CHANNEL_PREFIX$stageId"
        triggerEvent(channelName, EVENT_SYSTEM, data)
    }

    /**
     * Send a system message to the global site channel
     */
    fun sendSiteSystemMessage(data: Any) {
        triggerEvent(GLOBAL_CHANNEL, EVENT_SYSTEM, data)
    }

    /**
     * Send a private notification to a specific user
     */
    fun sendUserNotification(userId: String, data: Any) {
        val channelName = "$USER_CHANNEL_PREFIX$userId"
        triggerEvent(channelName, EVENT_NOTIFICATION, data)
    }

    /**
     * Send notifications to multiple users
     */
    fun sendUserNotifications(userIds: List<String>, data: Any) {
        userIds.forEach { userId ->
            sendUserNotification(userId, data)
        }
    }

    /**
     * Generic method to trigger an event on a channel
     */
    private fun triggerEvent(channel: String, event: String, data: Any) {
        try {
            pusher.trigger(channel, event, data)
            logger.debug("Triggered event '$event' on channel '$channel'")
        } catch (e: Exception) {
            logger.error("Failed to trigger event '$event' on channel '$channel': ${e.message}", e)
        }
    }

    /**
     * Trigger event on multiple channels at once
     */
    fun triggerMultiChannel(channels: List<String>, event: String, data: Any) {
        try {
            pusher.trigger(channels, event, data)
            logger.debug("Triggered event '$event' on ${channels.size} channels")
        } catch (e: Exception) {
            logger.error("Failed to trigger event '$event' on multiple channels: ${e.message}", e)
        }
    }
}
