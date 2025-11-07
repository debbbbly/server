package com.debbly.server.match

import com.debbly.server.match.model.Match
import com.debbly.server.pusher.model.PusherMessageType.*
import com.debbly.server.pusher.model.PusherEventName.MATCH_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.toNotificationDto
import com.debbly.server.pusher.service.PusherService
import org.springframework.stereotype.Service

@Service
class MatchNotificationService(
    private val pusherService: PusherService
) {

    fun notifyMatchFound(match: Match) {
        val userIds = match.opponents.map { it.userId }
        val message = message(MATCH_FOUND, match.toNotificationDto())
        pusherService.sendUserNotifications(userIds, MATCH_EVENT, message)
    }

    fun notifyOpponentAccepted(match: Match, userId: String) {
        val otherUserIds = match.opponents.filter { it.userId != userId }.map { it.userId }
        val data = mapOf(
            "matchId" to match.matchId,
            "acceptedByUserId" to userId
        )
        val message = message(MATCH_ACCEPTED, data)
        pusherService.sendUserNotifications(otherUserIds, MATCH_EVENT, message)
    }

    fun notifyMatchAcceptedAll(match: Match) {
        val userIds = match.opponents.map { it.userId }
        pusherService.sendUserNotifications(
            userIds,
            MATCH_EVENT,
            message(MATCH_ACCEPTED_ALL, match.toNotificationDto())
        )
    }

    fun notifyMatchTimeout(match: Match) {
        val userIds = match.opponents.map { it.userId }
        val data = mapOf("matchId" to match.matchId)
        pusherService.sendUserNotifications(userIds, MATCH_EVENT, message(MATCH_EXPIRED, data))
    }

    fun notifyMatchCancelled(match: Match, cancelledBy: String, reason: String) {
        val userIds = match.opponents.map { it.userId }
        val data = mapOf(
            "reason" to reason,
            "cancelledBy" to cancelledBy
        )
        val message = message(MATCH_EXPIRED, data)
        pusherService.sendUserNotifications(userIds, MATCH_EVENT, message)
    }
}