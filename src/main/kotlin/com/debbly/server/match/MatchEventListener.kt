package com.debbly.server.match

import com.debbly.server.livekit.LiveKitService
import com.debbly.server.match.event.MatchAcceptedAllEvent
import com.debbly.server.match.event.MatchAcceptedEvent
import com.debbly.server.match.event.MatchFoundEvent
import com.debbly.server.stage.StageService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MatchEventListener(
    private val matchNotificationService: MatchNotificationService,
    private val liveKitService: LiveKitService,
    private val stageService: StageService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Async("matchEventExecutor")
    fun handleMatchAccepted(event: MatchAcceptedEvent) {
        logger.info("MatchAcceptedEvent for match: ${event.match.matchId}, accepted by: ${event.acceptedByUserId}")

        try {
            matchNotificationService.notifyOpponentAccepted(event.match, event.acceptedByUserId)
        } catch (e: Exception) {
            logger.error("Failed to handle MatchAcceptedEvent for match ${event.match.matchId}", e)
        }
    }

    @EventListener
    @Async("matchEventExecutor")
    fun handleMatchFound(event: MatchFoundEvent) {
        logger.info("MatchFoundEvent for match: ${event.match.matchId}")

        try {
            val room = liveKitService.createRoom(event.match.matchId)
            if (room != null) {
                matchNotificationService.notifyMatchFound(event.match)
            } else {
                logger.error("Failed to create LiveKit room for match: ${event.match.matchId}")
            }
        } catch (e: Exception) {
            logger.error("Failed to handle MatchFoundEvent for match ${event.match.matchId}", e)
        }
    }

    @EventListener
    @Async("matchEventExecutor")
    fun handleMatchAcceptedAll(event: MatchAcceptedAllEvent) {
        logger.info("MatchAcceptedAllEvent for match: ${event.match.matchId}")

        try {
            stageService.createStage(event.match)
            matchNotificationService.notifyMatchAcceptedAll(event.match)
        } catch (e: Exception) {
            logger.error("Failed to handle MatchAcceptedAllEvent for match ${event.match.matchId}", e)
        }
    }
}