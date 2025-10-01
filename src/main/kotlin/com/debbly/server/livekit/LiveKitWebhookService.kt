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
            "room_started" -> { }//logger.info("Room started ${event.room.name}")
            "room_finished" -> {} // logger.info("Room finished: ${event.room.name}")
            "participant_joined" -> {
                stageService.onUserJoined(event.participant.identity, event.room.name)
            }

            "participant_left" -> {
                stageService.onUserLeft(event.participant.identity, event.room.name)
            }
//            "track_published" -> logger.info("Track published: ${event.track.sid} by ${event.participant.identity} in room ${event.room.name}")
//            "track_unpublished" -> logger.info("Track unpublished: ${event.track.sid} by ${event.participant.identity} in room ${event.room.name}")
            "egress_started" -> {
                logger.info("🎬 Egress started: ${event.egressInfo.egressId}")
                logger.info("   Room: ${event.egressInfo.roomName}")
                logger.info("   Status: ${event.egressInfo.status}")
            }
            "egress_updated" -> {
                logger.info("📹 Egress updated: ${event.egressInfo.egressId}")
                logger.info("   Room: ${event.egressInfo.roomName}")
                logger.info("   Status: ${event.egressInfo.status}")
            }
            "egress_ended" -> {
                logger.info("🎬 Egress ended: ${event.egressInfo.egressId}")
                logger.info("   Room: ${event.egressInfo.roomName}")
                logger.info("   Status: ${event.egressInfo.status}")
                logger.info("   StartedAt: ${event.egressInfo.startedAt}")
                logger.info("   EndedAt: ${event.egressInfo.endedAt}")
                logger.info("   Error: ${event.egressInfo.error}")
                logger.info("   FileResults: ${event.egressInfo.fileResultsList}")
                event.egressInfo.fileResultsList.forEach { fileResult ->
                    logger.info("   📁 File: ${fileResult.filename}")
                    logger.info("      Size: ${fileResult.size} bytes")
                    logger.info("      Location: ${fileResult.location}")
                }
            }
//            "ingress_started" -> logger.info("Ingress started: ${event.ingressInfo.ingressId}")
//            "ingress_ended" -> logger.info("Ingress ended: ${event.ingressInfo.ingressId}")
            else -> {}//logger.warn("Unknown webhook event: ${event.event}")
        }
    }

}