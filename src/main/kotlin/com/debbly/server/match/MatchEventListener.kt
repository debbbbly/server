package com.debbly.server.match

import com.debbly.server.livekit.LiveKitService
import com.debbly.server.match.event.MatchAcceptedAllEvent
import com.debbly.server.match.event.MatchAcceptedEvent
import com.debbly.server.match.event.MatchFoundEvent
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.pusher.model.PusherEventName.EVENT_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.EVENT_MATCHED
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.stage.StageService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MatchEventListener(
    private val matchNotificationService: MatchNotificationService,
    private val liveKitService: LiveKitService,
    private val stageService: StageService,
    private val matchRepository: MatchRepository,
    private val pusherService: PusherService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Async("matchEventExecutor")
    fun handleMatchAccepted(event: MatchAcceptedEvent) {
        logger.debug("Handling MatchAcceptedEvent for match ${event.match.matchId}")

        try {
            matchNotificationService.notifyOpponentAccepted(event.match, event.acceptedByUserId)
        } catch (e: Exception) {
            logger.error("Failed to handle MatchAcceptedEvent for match ${event.match.matchId}", e)
        }
    }

    @EventListener
    @Async("matchEventExecutor")
    fun handleMatchFound(event: MatchFoundEvent) {
        logger.debug("Handling MatchFoundEvent for match ${event.match.matchId}")

        try {
            val room = liveKitService.createRoom(event.match.matchId)
            if (room != null) {
                matchNotificationService.notifyMatchFound(event.match)
            } else {
                logger.error("Failed to create LiveKit room for match: ${event.match.matchId}")
                matchNotificationService.notifyMatchFailed(event.match, "Failed to create video room")
                matchRepository.delete(event.match.matchId)
            }
        } catch (e: Exception) {
            logger.error("Failed to handle MatchFoundEvent for match ${event.match.matchId}", e)
            try {
                matchNotificationService.notifyMatchFailed(event.match, "Internal error: ${e.message}")
                matchRepository.delete(event.match.matchId)
            } catch (notifyError: Exception) {
                logger.error("Failed to notify match failure for match ${event.match.matchId}", notifyError)
            }
        }
    }

    @EventListener
    @Async("matchEventExecutor")
    fun handleMatchAcceptedAll(event: MatchAcceptedAllEvent) {
        logger.debug("Handling MatchAcceptedAllEvent for match ${event.match.matchId}")

        try {
            stageService.createStage(event.match)
            matchNotificationService.notifyMatchAcceptedAll(event.match)

            event.match.eventId?.let { eventId ->
                pusherService.sendRawChannelMessage(
                    "event:$eventId",
                    EVENT_EVENT,
                    message(EVENT_MATCHED, mapOf(
                        "eventId" to eventId,
                        "matchId" to event.match.matchId,
                        "stageId" to event.match.matchId,
                    ))
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to handle MatchAcceptedAllEvent for match ${event.match.matchId}", e)
            try {
                matchNotificationService.notifyMatchFailed(event.match, "Failed to create debate stage")
                matchRepository.delete(event.match.matchId)
            } catch (notifyError: Exception) {
                logger.error("Failed to notify match failure for match ${event.match.matchId}", notifyError)
            }
        }
    }
}
