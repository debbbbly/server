package com.debbly.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class WebSocketSessionService(
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val userSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionHeartbeats = ConcurrentHashMap<String, Long>()

    companion object {
        private const val SESSION_EXPIRY_SECONDS = 300L // 5 minutes
        private const val HEARTBEAT_INTERVAL_SECONDS = 30L // 30 seconds
    }

    /**
     * Register a new WebSocket session
     */
    fun registerSession(userId: String, session: WebSocketSession) {
        val sessionId = session.id

        logger.info("Registering WebSocket session: userId={}, sessionId={}", userId, sessionId)

        // Store session locally
        activeSessions[sessionId] = session

        // Track user sessions
        userSessions.computeIfAbsent(userId) { mutableSetOf() }.add(sessionId)

        // Track heartbeat for this session
        updateSessionHeartbeat(sessionId)

        // Send initial ping
        sendPing(sessionId)

        logger.info("User {} now has {} active sessions", userId, userSessions[userId]?.size ?: 0)
    }

    /**
     * Unregister a WebSocket session
     */
    fun unregisterSession(userId: String, session: WebSocketSession) {
        val sessionId = session.id

        logger.info("Unregistering WebSocket session: userId={}, sessionId={}", userId, sessionId)

        // Remove session locally
        activeSessions.remove(sessionId)

        // Remove from user sessions
        userSessions[userId]?.remove(sessionId)

        // Clean up if no more sessions for user
        if (userSessions[userId]?.isEmpty() == true) {
            userSessions.remove(userId)
            logger.info("User {} is now offline (no active sessions)", userId)
        } else {
            logger.info("User {} still has {} active sessions", userId, userSessions[userId]?.size ?: 0)
        }

        // Remove heartbeat tracking
        removeSessionHeartbeat(sessionId)
    }

    /**
     * Handle ping response (pong) from client
     */
    fun handlePongResponse(sessionId: String) {
        updateSessionHeartbeat(sessionId)
        logger.debug("Received pong from session: {}", sessionId)
    }

    /**
     * Send message to all sessions of a user
     */
    fun sendMessageToUser(userId: String, message: Any) {
        val sessionIds = userSessions[userId] ?: return
        val jsonMessage = objectMapper.writeValueAsString(message)

        sessionIds.forEach { sessionId ->
            val session = activeSessions[sessionId]
            if (session?.isOpen == true) {
                try {
                    session.sendMessage(TextMessage(jsonMessage))
                    logger.debug("Sent message to session {} for user {}", sessionId, userId)
                } catch (e: Exception) {
                    logger.error("Error sending message to session {} for user {}: {}", sessionId, userId, e.message)
                    // Mark session for cleanup
                    markSessionForCleanup(sessionId, userId)
                }
            } else {
                logger.warn("Session {} for user {} is closed, marking for cleanup", sessionId, userId)
                markSessionForCleanup(sessionId, userId)
            }
        }
    }

    /**
     * Send message to multiple users
     */
    fun sendMessageToUsers(userIds: List<String>, message: Any) {
        userIds.forEach { userId ->
            sendMessageToUser(userId, message)
        }
    }

    fun getOnlineUserIds(): Set<String> = userSessions.filter { it.value.isNotEmpty() }.keys

    fun isUserOnline(userId: String): Boolean {
        return userSessions[userId]?.isNotEmpty() ?: false
    }

    /**
     * Get count of online users
     */
    fun getOnlineUserCount(): Int {
        return getOnlineUserIds().size
    }

    /**
     * Send ping to all active sessions
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_SECONDS * 1000)
    fun sendPings() {
        logger.debug("Sending pings to {} active sessions", activeSessions.size)

        activeSessions.values.forEach { session ->
            if (session.isOpen) {
                sendPing(session.id)
            }
        }
    }

    /**
     * Clean up expired sessions
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    fun cleanupExpiredSessions() {
        logger.debug("Running session cleanup...")

        val currentTime = Instant.now(clock).epochSecond
        val expiredSessions = mutableListOf<String>()

        // Check in-memory heartbeats for expired sessions
        try {
            sessionHeartbeats.forEach { (sessionId, lastHeartbeat) ->
                if ((currentTime - lastHeartbeat) > SESSION_EXPIRY_SECONDS) {
                    expiredSessions.add(sessionId)
                }
            }

            // Clean up expired sessions
            expiredSessions.forEach { sessionId ->
                logger.info("Cleaning up expired session: {}", sessionId)
                val session = activeSessions[sessionId]
                if (session != null) {
                    val userId = findUserIdBySessionId(sessionId)
                    if (userId != null) {
                        try {
                            session.close()
                        } catch (e: Exception) {
                            logger.warn("Error closing expired session {}: {}", sessionId, e.message)
                        }
                        unregisterSession(userId, session)
                    }
                }
                removeSessionHeartbeat(sessionId)
            }

            if (expiredSessions.isNotEmpty()) {
                logger.info("Cleaned up {} expired sessions", expiredSessions.size)
            }

        } catch (e: Exception) {
            logger.error("Error during session cleanup: {}", e.message)
        }
    }

    /**
     * Update the heartbeat timestamp for a session
     */
    private fun updateSessionHeartbeat(sessionId: String) {
        sessionHeartbeats[sessionId] = Instant.now(clock).epochSecond
    }

    /**
     * Remove heartbeat tracking for a session
     */
    private fun removeSessionHeartbeat(sessionId: String) {
        sessionHeartbeats.remove(sessionId)
    }


    private fun sendPing(sessionId: String) {
        val session = activeSessions[sessionId]
        if (session?.isOpen == true) {
            try {
                val pingMessage = MatchingMessage(
                    type = MessageType.PING,
                    message = "ping"
                )
                val jsonMessage = objectMapper.writeValueAsString(pingMessage)
                session.sendMessage(TextMessage(jsonMessage))
            } catch (e: Exception) {
                logger.error("Error sending ping to session {}: {}", sessionId, e.message)
            }
        }
    }

    private fun markSessionForCleanup(sessionId: String, userId: String) {
        activeSessions.remove(sessionId)
        userSessions[userId]?.remove(sessionId)

        if (userSessions[userId]?.isEmpty() == true) {
            userSessions.remove(userId)

        }
    }

    private fun findUserIdBySessionId(sessionId: String): String? {
        return userSessions.entries.find { (_, sessionIds) ->
            sessionIds.contains(sessionId)
        }?.key
    }
}
