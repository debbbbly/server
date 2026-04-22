package com.debbly.server.match

import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.match.model.MatchStatus
import com.debbly.server.pusher.model.PusherEventName
import com.debbly.server.pusher.model.PusherMessage
import com.debbly.server.pusher.service.PusherService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant

class MatchNotificationServiceTest {

    private val pusher: PusherService = mock()
    private val service = MatchNotificationService(pusher)

    private fun match() = Match(
        matchId = "m1",
        claim = Match.MatchClaim("c1", "t"),
        status = MatchStatus.PENDING,
        opponents = listOf(
            Match.MatchOpponent("u1", "alice", null, null, MatchOpponentStatus.PENDING, 0),
            Match.MatchOpponent("u2", "bob", null, null, MatchOpponentStatus.PENDING, 0)
        ),
        ttl = 60,
        updatedAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    @Test
    fun `notifyMatchFound sends to all opponents`() {
        service.notifyMatchFound(match())
        verify(pusher).sendUserNotifications(eq(listOf("u1", "u2")), eq(PusherEventName.MATCH_EVENT), any())
    }

    @Test
    fun `notifyOpponentAccepted excludes accepting user`() {
        service.notifyOpponentAccepted(match(), "u1")
        verify(pusher).sendUserNotifications(eq(listOf("u2")), eq(PusherEventName.MATCH_EVENT), any())
    }

    @Test
    fun `notifyMatchAcceptedAll sends to all opponents`() {
        service.notifyMatchAcceptedAll(match())
        verify(pusher).sendUserNotifications(eq(listOf("u1", "u2")), eq(PusherEventName.MATCH_EVENT), any())
    }

    @Test
    fun `notifyMatchTimeout sends to all opponents`() {
        service.notifyMatchTimeout(match())
        verify(pusher).sendUserNotifications(eq(listOf("u1", "u2")), eq(PusherEventName.MATCH_EVENT), any())
    }

    @Test
    fun `notifyMatchCancelled sends to all opponents`() {
        service.notifyMatchCancelled(match(), "u1", "cancelled by host")
        verify(pusher).sendUserNotifications(eq(listOf("u1", "u2")), eq(PusherEventName.MATCH_EVENT), any())
    }

    @Test
    fun `notifyMatchFailed sends to all opponents`() {
        service.notifyMatchFailed(match(), "reason")
        verify(pusher).sendUserNotifications(eq(listOf("u1", "u2")), eq(PusherEventName.MATCH_EVENT), any())
    }

    @Test
    fun `notifyStillWaiting no-ops on empty list`() {
        service.notifyStillWaiting(emptyList())
        verify(pusher, never()).sendUserNotifications(any(), any(), any<PusherMessage>())
    }

    @Test
    fun `notifyStillWaiting sends when list non-empty`() {
        service.notifyStillWaiting(listOf("u1"))
        verify(pusher).sendUserNotifications(eq(listOf("u1")), eq(PusherEventName.MATCH_EVENT), any())
    }
}
