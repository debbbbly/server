package com.debbly.server.livekit

import com.debbly.server.stage.StageService
import livekit.LivekitWebhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LiveKitWebhookService(
    private val stageService: StageService
) {
    companion object {
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun processWebhookEvent(event: LivekitWebhook.WebhookEvent) {
        when (event.event) {
            "room_started" -> logger.debug("Room started: ${event.room.name}")
            "room_finished" -> logger.debug("Room finished: ${event.room.name}")
            "participant_joined" -> {
                stageService.onUserJoined(event.participant.identity, event.room.name)
            }
            "participant_left" -> {
                stageService.onParticipantLeft(event.participant.identity, event.room.name)
            }
            "egress_started" -> {
                logger.debug("Egress started: ${event.egressInfo.egressId}, room: ${event.egressInfo.roomName}")
            }
            "egress_updated" -> {
                logger.debug("Egress updated: ${event.egressInfo.egressId}, status: ${event.egressInfo.status}")
            }
            "egress_ended" -> {
                val hasError = event.egressInfo.error.isNotEmpty()
                if (hasError) {
                    logger.warn("Egress ended with error: ${event.egressInfo.egressId}, error: ${event.egressInfo.error}")
                } else {
                    logger.debug("Egress ended: ${event.egressInfo.egressId}, room: ${event.egressInfo.roomName}")
                }
            }
            else -> logger.debug("Unhandled webhook event: ${event.event}")
        }
    }

}