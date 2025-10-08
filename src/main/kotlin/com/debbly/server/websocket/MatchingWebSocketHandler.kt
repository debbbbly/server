package com.debbly.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class MatchingWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val webSocketSessionService: WebSocketSessionService
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = extractUserIdFromSession(session)
        webSocketSessionService.registerSession(userId, session)

        if (userId.startsWith("guest_")) {
            logger.info("WebSocket connection established for guest: {}", userId)
            webSocketSessionService.sendMessageToUser(userId, MatchingMessage(
                type = MessageType.CONNECTION_ESTABLISHED,
                message = "Connected to matching service as guest"
            ))
        } else {
            logger.info("WebSocket connection established for authenticated user: {}", userId)
            webSocketSessionService.sendMessageToUser(userId, MatchingMessage(
                type = MessageType.CONNECTION_ESTABLISHED,
                message = "Connected to matching service"
            ))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = extractUserIdFromSession(session)
        webSocketSessionService.unregisterSession(userId, session)
        logger.info("WebSocket connection closed for user: {}", userId)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = extractUserIdFromSession(session)
        logger.debug("Received message from user {}: {}", userId, message.payload)

        // Handle incoming messages if needed (e.g., ping/pong, heartbeat responses)
        try {
            val incomingMessage = objectMapper.readValue(message.payload, IncomingMessage::class.java)
            when (incomingMessage.type) {
                "PING" -> {
                    webSocketSessionService.sendMessageToUser(userId, MatchingMessage(MessageType.PONG, "pong"))
                }
                "PONG" -> {
                    webSocketSessionService.handlePongResponse(session.id)
                }
                else -> {
                    logger.debug("Unhandled message type: {}", incomingMessage.type)
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling WebSocket message: {}", e.message)
        }
    }

    private fun extractUserIdFromSession(session: WebSocketSession): String {
        // Try authenticated user first
        session.principal?.name?.let { return it }

        // Generate guest ID for unauthenticated users
        return "guest_${session.id}"
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
    PING,
    PONG
}

data class MatchingMessage(
    val type: MessageType,
    val message: String,
    val data: Any? = null
)
/*
match_found	| payload: {} | Server notifies you that a match was found (opponent matched)	Server →  Both participants
match_accepted | payload: {} | 	You or your opponent clicked “Accept”	Server → both participants
match_ready | payload: {} | 	Both sides accepted the match — ready to start debate	Server →  Both participants
match_expired | payload: {} | 	One side didn’t accept before timeout	Server →  Both participants

(I also want to send match_ready to other users on the platform so that they join and probably even not regestred one)

debate_started Both are redirected / joined the debate room Server →  Both participants
debate_ended Debate ended (either normal finish or due to drop/timeout) Server →  Both participants




 */