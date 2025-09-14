package com.debbly.server.match

import com.debbly.server.match.model.Match
import com.debbly.server.websocket.MatchingMessage
import com.debbly.server.websocket.MatchingService
import com.debbly.server.websocket.MessageType
import org.springframework.stereotype.Service

@Service
class MatchNotificationService(
    private val matchingService: MatchingService
) {

    fun notifyMatchFound(match: Match) {
        val userIds = match.opponents.map { it.userId }
        matchingService.sendMatchingMessage(userIds, MatchingMessage(
            type = MessageType.MATCH_FOUND,
            message = "Match found!",
            data = match
        ))
    }

    fun notifyOpponentAccepted(match: Match, userId: String) {
        val otherUserIds = match.opponents.filter { it.userId != userId }.map { it.userId }
        matchingService.sendMatchingMessage(otherUserIds, MatchingMessage(
            type = MessageType.MATCH_ACCEPTED,
            message = "Opponent has accepted the match",
            data = mapOf(
                "matchId" to match.matchId,
                "acceptedBy" to userId
            )
        ))
    }

    fun notifyMatchConfirmedAll(match: Match) {
        val userIds = match.opponents.map { it.userId }
        matchingService.sendMatchingMessage(userIds, MatchingMessage(
            type = MessageType.MATCH_ACCEPTED_ALL,
            message = "Match confirmed! Starting debate room...",
            data = match
        ))
    }

    fun notifyMatchTimeout(match: Match) {
        val userIds = match.opponents.map { it.userId }
        matchingService.sendMatchingMessage(userIds, MatchingMessage(
            type = MessageType.MATCH_EXPIRED,
            message = "Match expired, returning to queue",
            data = mapOf("reason" to "timeout")
        ))
    }

    fun notifyMatchCancelled(match: Match, cancelledBy: String, reason: String) {
        val userIds = match.opponents.map { it.userId }
        matchingService.sendMatchingMessage(userIds, MatchingMessage(
            type = MessageType.MATCH_EXPIRED,
            message = "Match cancelled, returning to queue",
            data = mapOf(
                "reason" to reason,
                "cancelledBy" to cancelledBy
            )
        ))
    }
}