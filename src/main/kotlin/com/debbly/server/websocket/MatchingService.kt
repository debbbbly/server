package com.debbly.server.websocket

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MatchingService(
    private val webSocketSessionService: WebSocketSessionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Send a matching message to a specific user
     */
    fun sendMatchingMessage(userId: String, message: MatchingMessage) {
        try {
            webSocketSessionService.sendMessageToUser(userId, message)
            logger.debug("Sent matching message to user {}: {}", userId, message.type)
        } catch (e: Exception) {
            logger.error("Error sending matching message to user {}: {}", userId, e.message)
        }
    }

    /**
     * Send matching message to multiple users
     */
    fun sendMatchingMessage(userIds: List<String>, message: MatchingMessage) {
        try {
            webSocketSessionService.sendMessageToUsers(userIds, message)
            logger.debug("Sent matching message to {} users: {}", userIds.size, message.type)
        } catch (e: Exception) {
            logger.error("Error sending matching message to users {}: {}", userIds, e.message)
        }
    }

    /**
     * Notify user about successful match
     */
    fun notifyMatchFound(userId: String, matchData: Any) {
        val message = MatchingMessage(
            type = MessageType.MATCH_FOUND,
            message = "Match found!",
            data = matchData
        )
        sendMatchingMessage(userId, message)
    }

    /**
     * Notify users about match acceptance
     */
    fun notifyMatchAccepted(userIds: List<String>, matchData: Any) {
        val message = MatchingMessage(
            type = MessageType.MATCH_ACCEPTED,
            message = "Match accepted!",
            data = matchData
        )
        sendMatchingMessage(userIds, message)
    }

    /**
     * Notify users that all participants have accepted
     */
    fun notifyMatchAcceptedAll(userIds: List<String>, matchData: Any) {
        val message = MatchingMessage(
            type = MessageType.MATCH_ACCEPTED_ALL,
            message = "All participants accepted! Starting match...",
            data = matchData
        )
        sendMatchingMessage(userIds, message)
    }

    /**
     * Notify users about match expiration
     */
    fun notifyMatchExpired(userIds: List<String>, reason: String = "Match expired") {
        val message = MatchingMessage(
            type = MessageType.MATCH_EXPIRED,
            message = reason
        )
        sendMatchingMessage(userIds, message)
    }

    /**
     * Get all connected user IDs for matching purposes
     */
    fun getConnectedUserIds(): Set<String> {
        return webSocketSessionService.getOnlineUserIds()
    }
}