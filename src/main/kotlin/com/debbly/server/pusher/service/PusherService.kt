package com.debbly.server.pusher.service

import com.debbly.server.pusher.model.PusherEventName
import com.debbly.server.pusher.model.PusherMessage
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
        const val GLOBAL_CHANNEL = "public-global"
        const val USER_CHANNEL_PREFIX = "private-user-"
        const val GLOBAL_CHANNEL_ID = "global"
    }

    fun sendChannelMessage(channelId: String, eventName: PusherEventName, message: PusherMessage) {
        val channel = if (channelId == GLOBAL_CHANNEL_ID) {
            GLOBAL_CHANNEL
        } else {
            "$STAGE_CHANNEL_PREFIX$channelId"
        }
        triggerEvent(channel, eventName, message)
    }

    fun sendUserNotification(userId: String, eventName: PusherEventName, message: PusherMessage) {
        val channelName = "$USER_CHANNEL_PREFIX$userId"
        triggerEvent(channelName, eventName, message)
    }

    fun sendUserNotifications(userIds: List<String>, eventName: PusherEventName, message: PusherMessage) {
        userIds.forEach { userId ->
            sendUserNotification(userId, eventName, message)
        }
    }

    fun sendRawChannelMessage(channel: String, eventName: PusherEventName, message: PusherMessage) {
        triggerEvent(channel, eventName, message)
    }

    private fun triggerEvent(channel: String, eventName: PusherEventName, data: Any) {
        try {
            pusher.trigger(channel, eventName.name, data)
            logger.debug("Triggered event '$eventName' on channel '$channel'")
        } catch (e: Exception) {
            logger.error("Failed to trigger event '$eventName' on channel '$channel': ${e.message}", e)
        }
    }
}
