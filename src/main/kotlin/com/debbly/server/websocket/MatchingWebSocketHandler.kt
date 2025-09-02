package com.debbly.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class MatchingWebSocketHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val userSessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = extractUserIdFromSession(session)
        if (userId != null) {
            userSessions[userId] = session
            logger.info("WebSocket connection established for user: {}", userId)

            sendMessage(userId, MatchingMessage(
                type = MessageType.CONNECTION_ESTABLISHED,
                message = "Connected to matching service"
            ))
        } else {
            logger.warn("No userId found in WebSocket session, closing connection")
            session.close()
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = findUserIdBySession(session)
        if (userId != null) {
            userSessions.remove(userId)
            logger.info("WebSocket connection closed for user: {}", userId)
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = findUserIdBySession(session)
        logger.debug("Received message from user {}: {}", userId, message.payload)
        
        // Handle incoming messages if needed (e.g., ping/pong, acknowledgments)
        try {
            val incomingMessage = objectMapper.readValue(message.payload, IncomingMessage::class.java)
            when (incomingMessage.type) {
                "PING" -> {
                    if (userId != null) {
                        sendMessage(userId, MatchingMessage(MessageType.PONG, "pong"))
                    }
                }
                else -> {
                    logger.debug("Unhandled message type: {}", incomingMessage.type)
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling WebSocket message: {}", e.message)
        }
    }

    /**
     * Send a message to a specific user
     */
    fun sendMessage(userId: String, message: MatchingMessage) {
        val session = userSessions[userId]
        if (session?.isOpen == true) {
            try {
                val jsonMessage = objectMapper.writeValueAsString(message)
                session.sendMessage(TextMessage(jsonMessage))
                logger.debug("Sent message to user {}: {}", userId, message.type)
            } catch (e: Exception) {
                logger.error("Error sending WebSocket message to user {}: {}", userId, e.message)
            }
        } else {
            logger.warn("No active WebSocket session for user: {}", userId)
        }
    }

    /**
     * Send a message to multiple users
     */
    fun sendMessage(userIds: List<String>, message: MatchingMessage) {
        userIds.forEach { userId ->
            sendMessage(userId, message)
        }
    }

    /**
     * Get all connected user IDs
     */
    fun getConnectedUserIds(): Set<String> {
        return userSessions.keys.toSet()
    }

    private fun extractUserIdFromSession(session: WebSocketSession): String? {
        return session.principal?.name
    }

    private fun findUserIdBySession(session: WebSocketSession): String? {
        return userSessions.entries.find { it.value == session }?.key
    }

    data class IncomingMessage(
        val type: String,
        val data: Any? = null
    )
}

enum class MessageType {
    CONNECTION_ESTABLISHED,
    MATCH_FOUND,
    MATCH_ACCEPTED,
    MATCH_ACCEPTED_ALL,
    MATCH_EXPIRED,
    PONG
}

data class MatchingMessage(
    val type: MessageType,
    val message: String,
    val data: Any? = null
)