package com.debbly.server.event

import com.debbly.server.event.model.EventAcceptanceStatus
import com.debbly.server.event.repository.EventParticipantJpaRepository
import com.debbly.server.match.event.MatchExpiredEvent
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.pusher.model.PusherEventName.EVENT_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.EVENT_QUEUE_UPDATED
import com.debbly.server.pusher.service.PusherService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class EventMatchExpiredListener(
    private val eventParticipantRepository: EventParticipantJpaRepository,
    private val pusherService: PusherService,
    private val clock: Clock,
) {

    @EventListener
    fun handleMatchExpired(event: MatchExpiredEvent) {
        val match = event.match
        val eventId = match.eventId ?: return

        // The participant who ignored the match is the opponent with PENDING status.
        // The host was pre-set to ACCEPTED when the match was created.
        val noShowUserId = match.opponents
            .firstOrNull { it.status == MatchOpponentStatus.PENDING }
            ?.userId ?: return

        val participant = eventParticipantRepository.findByEventIdAndUserId(eventId, noShowUserId)
            ?: return

        if (participant.status != EventAcceptanceStatus.MATCHED) return

        eventParticipantRepository.save(
            participant.copy(
                status = EventAcceptanceStatus.NO_SHOW,
                updatedAt = Instant.now(clock)
            )
        )

        pusherService.sendRawChannelMessage(
            "event:$eventId",
            EVENT_EVENT,
            message(EVENT_QUEUE_UPDATED, mapOf("eventId" to eventId, "noShowUserId" to noShowUserId))
        )
    }
}
