package com.debbly.server.livekit

import com.debbly.server.IdService
import com.debbly.server.LiveKitConfig
import io.livekit.server.*
import livekit.LivekitWebhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LiveKitService(
    private val liveKitConfig: LiveKitConfig,
    private val idService: IdService
) {
    companion object {
        private const val DEFAULT_TOKEN_TTL: Long = 60 * 15;
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getToken(userId: String?, stageId: String, isHost: Boolean): String {
        val token = AccessToken(liveKitConfig.apiKey, liveKitConfig.apiSecret).apply {
            val tokenUserId = userId ?: "guest_${idService.getId()}"

            name = tokenUserId
            identity = tokenUserId
            ttl = DEFAULT_TOKEN_TTL
            metadata = null

            addGrants(
                RoomJoin(true),
                RoomName(stageId),
                CanPublish(isHost),
                CanPublishData(isHost),
            )
        }

        return token.toJwt()
    }

    fun processWebhookEvent(event: LivekitWebhook.WebhookEvent) {
        when (event.event) {
            "room_started" -> logger.info("Room started ${event.room.name}")
            "room_finished" -> logger.info("Room finished: ${event.room.name}")
            "participant_joined" -> logger.info("Participant joined: ${event.participant.identity} to room ${event.room.name}")
            "participant_left" -> logger.info("Participant left: ${event.participant.identity} from room ${event.room.name}")
            "track_published" -> logger.info("Track published: ${event.track.sid} by ${event.participant.identity} in room ${event.room.name}")
            "track_unpublished" -> logger.info("Track unpublished: ${event.track.sid} by ${event.participant.identity} in room ${event.room.name}")
            "egress_started" -> logger.info("Egress started: ${event.egressInfo.egressId}")
            "egress_updated" -> logger.info("Egress updated: ${event.egressInfo.egressId}")
            "egress_ended" -> logger.info("Egress ended: ${event.egressInfo.egressId}")
            "ingress_started" -> logger.info("Ingress started: ${event.ingressInfo.ingressId}")
            "ingress_ended" -> logger.info("Ingress ended: ${event.ingressInfo.ingressId}")
            else -> logger.warn("Unknown webhook event: ${event.event}")
        }
    }
}