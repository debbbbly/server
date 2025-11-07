package com.debbly.server.match

import com.debbly.server.match.model.Match
import com.debbly.server.pusher.dto.toNotificationDto
import com.debbly.server.pusher.service.PusherService
import org.springframework.stereotype.Service

@Service
class MatchNotificationService(
    private val pusherService: PusherService
) {

    fun notifyMatchFound(match: Match) {
        val userIds = match.opponents.map { it.userId }
        val notification = mapOf(
            "type" to "MATCH_FOUND",
//            "message" to "Match found!",
            "data" to match.toNotificationDto()
        )
        pusherService.sendUserNotifications(userIds, notification)
    }

    fun notifyOpponentAccepted(match: Match, userId: String) {
        val otherUserIds = match.opponents.filter { it.userId != userId }.map { it.userId }
        val notification = mapOf(
            "type" to "MATCH_ACCEPTED",
//            "message" to "Opponent has accepted the match",
            "data" to mapOf(
                "matchId" to match.matchId,
                "acceptedByUserId" to userId
            )
        )
        pusherService.sendUserNotifications(otherUserIds, notification)
    }

    fun notifyMatchAcceptedAll(match: Match) {
        val userIds = match.opponents.map { it.userId }
        val notification = mapOf(
            "type" to "MATCH_ACCEPTED_ALL",
//            "message" to "Match confirmed! Starting debate room...",
            "data" to match.toNotificationDto()
        )
        pusherService.sendUserNotifications(userIds, notification)
    }

    fun notifyMatchTimeout(match: Match) {
        val userIds = match.opponents.map { it.userId }
        val notification = mapOf(
            "type" to "MATCH_EXPIRED",
//            "message" to "Match expired, returning to queue",
            "data" to mapOf(
                "matchId" to match.matchId,
            )
        )
        pusherService.sendUserNotifications(userIds, notification)
    }

    fun notifyMatchCancelled(match: Match, cancelledBy: String, reason: String) {
        val userIds = match.opponents.map { it.userId }
        val notification = mapOf(
            "type" to "MATCH_EXPIRED",
//            "message" to "Match cancelled, returning to queue",
            "data" to mapOf(
                "reason" to reason,
                "cancelledBy" to cancelledBy
            )
        )
        pusherService.sendUserNotifications(userIds, notification)
    }
}