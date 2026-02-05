package com.debbly.server.livekit

import com.debbly.server.IdService
import com.debbly.server.config.LiveKitConfig
import com.debbly.server.settings.SettingsService
import io.livekit.server.*
import livekit.LivekitModels
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LiveKitService(
    private val liveKitConfig: LiveKitConfig,
    private val idService: IdService,
    private val livekitRoomService: RoomServiceClient,
    private val settings: SettingsService,
) {
    companion object {
        private const val TOKEN_TTL_EXTRA_SECONDS: Long = 60;
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createRoom(stageId: String, emptyTimeoutSeconds: Int = 30, maxParticipants: Int = 30): LivekitModels.Room? {
        val call = livekitRoomService.createRoom(stageId, emptyTimeoutSeconds, maxParticipants)
        val response = call.execute()

        if (response.isSuccessful) {
            logger.info("Created room: $stageId with maxParticipants: $maxParticipants, empty timeout: ${emptyTimeoutSeconds}min")
            return response.body()
        } else {
            logger.error("Failed to create room $stageId: ${response.code()} ${response.message()}")
            return null
        }
    }

    fun getParticipants(stageId: String): List<LivekitModels.ParticipantInfo> {
        logger.debug("Attempting to get participants for room: $stageId")

        repeat(3) { attempt ->
            val call = livekitRoomService.listParticipants(stageId)
            val response = call.execute()

            if (response.isSuccessful) {
                val listParticipantsResponse = response.body()
                val participantsList = listParticipantsResponse ?: emptyList()
                logger.debug("Retrieved ${participantsList.size} participants for room $stageId")
                return participantsList
            } else if (response.code() == 404) {
                if (attempt < 2) {
                    logger.debug("Room $stageId not found (404) - retrying in 500ms (attempt ${attempt + 1}/3)")
                    Thread.sleep(500)
                } else {
                    logger.warn("Room $stageId not found in LiveKit (404) after 3 attempts. Room exists for joining but listParticipants API fails. This may be a LiveKit API configuration issue.")
                    return emptyList()
                }
            } else {
                logger.error("Failed to get participants for room $stageId: ${response.code()} ${response.message()}")
                return emptyList()
            }
        }
        return emptyList()
    }

    fun getToken(userId: String?, stageId: String, canPublish: Boolean, metadata: String): String {
        val token = AccessToken(liveKitConfig.apiKey, liveKitConfig.apiSecret).apply {
            val tokenUserId = userId ?: "guest_${idService.getId()}"

            name = tokenUserId
            identity = tokenUserId
            ttl = settings.getStageDuration().toLong() + TOKEN_TTL_EXTRA_SECONDS

            this.metadata = metadata

            addGrants(
                RoomJoin(true),
                RoomName(stageId),
                CanPublish(canPublish),
                CanPublishData(canPublish),
            )
        }

        return token.toJwt()
    }

    /**
     * End a room - kicks all participants and closes the room
     */
    fun endRoom(stageId: String): Boolean {
        val call = livekitRoomService.deleteRoom(stageId)
        val response = call.execute()

        if (response.isSuccessful) {
            logger.debug("Ended room $stageId")
            return true
        } else {
            logger.error("Failed to end room $stageId: ${response.code()} ${response.message()}")
            return false
        }
    }

    /**
     * Remove a specific participant from a room
     */
    fun removeParticipant(stageId: String, participantIdentity: String): Boolean {
        val call = livekitRoomService.removeParticipant(stageId, participantIdentity)
        val response = call.execute()

        if (response.isSuccessful) {
            logger.debug("Removed participant $participantIdentity from room $stageId")
            return true
        } else {
            logger.error("Failed to remove participant $participantIdentity from room $stageId: ${response.code()} ${response.message()}")
            return false
        }
    }

    /**
     * Mute a participant (audio, video, or both)
     */
    fun muteParticipant(stageId: String, participantIdentity: String, muted: Boolean): Boolean {
        val call = livekitRoomService.mutePublishedTrack(stageId, participantIdentity, "", muted)
        val response = call.execute()

        if (response.isSuccessful) {
            logger.debug("${if (muted) "Muted" else "Unmuted"} participant $participantIdentity in room $stageId")
            return true
        } else {
            logger.error("Failed to ${if (muted) "mute" else "unmute"} participant $participantIdentity in room $stageId: ${response.code()} ${response.message()}")
            return false
        }
    }

    /**
     * Get room info including metadata and settings
     */
    fun getRoomInfo(stageId: String): LivekitModels.Room? {
        val call = livekitRoomService.listRooms(listOf(stageId))
        val response = call.execute()

        if (response.isSuccessful) {
            val rooms = response.body()
            return rooms?.firstOrNull()
        } else {
            logger.error("Failed to get room info for $stageId: ${response.code()} ${response.message()}")
            return null
        }
    }
}


