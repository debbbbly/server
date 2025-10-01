package com.debbly.server.livekit

import com.debbly.server.IdService
import com.debbly.server.config.LiveKitConfig
import com.debbly.server.config.S3ConfigProperties
import io.livekit.server.*
import livekit.LivekitModels
import livekit.LivekitEgress
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LiveKitService(
    private val liveKitConfig: LiveKitConfig,
    private val s3Config: S3ConfigProperties,
    private val idService: IdService,
    private val livekitRoomService: RoomServiceClient,
    private val livekitEgressService: EgressServiceClient,
) {
    companion object {
        private const val DEFAULT_TOKEN_TTL: Long = 60 * 15;
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createRoom(stageId: String, maxParticipants: Int = 10, emptyTimeoutSeconds: Int = 10): LivekitModels.Room? {
        val call = livekitRoomService.createRoom(stageId, emptyTimeoutSeconds, maxParticipants)
        val response = call.execute()
        
        if (response.isSuccessful) {
            logger.info("Created room: $stageId with maxParticipants: $maxParticipants, timeout: ${emptyTimeoutSeconds}min")
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
                if (attempt > 0) {
                    logger.info("Retrieved ${participantsList.size} participants for room: $stageId (after ${attempt + 1} attempts)")
                } else {
                    logger.info("Retrieved ${participantsList.size} participants for room: $stageId")
                }
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

    fun startRoomEgress(
        stageId: String,

    ): LivekitEgress.EgressInfo? {
        logger.info("Starting egress for stage $stageId with S3 config: endpoint=${s3Config.endpoint}, bucket=${s3Config.bucket}, region=${s3Config.region}")

        val s3Upload = LivekitEgress.S3Upload.newBuilder()
            .setEndpoint(s3Config.endpoint)
            .setBucket(s3Config.bucket)
            .setRegion(s3Config.region)
            .setAccessKey(s3Config.accessKey)
            .setSecret(s3Config.secret)
            .setForcePathStyle(s3Config.forcePathStyle)
            .build()

        val filename = "recordings/${stageId}.mp4"
        logger.info("Egress will save to: $filename")

        val encodedFileOutput = LivekitEgress.EncodedFileOutput.newBuilder()
            .setFileType(LivekitEgress.EncodedFileType.MP4)
            .setFilepath(filename)
            .setS3(s3Upload)
            .build()

        val call = livekitEgressService.startRoomCompositeEgress(
            stageId,
            encodedFileOutput,
            "grid",
            LivekitEgress.EncodingOptionsPreset.H264_720P_30,
            null,
            false,
            false,
            ""
        )
        val response = call.execute()
        
        if (response.isSuccessful) {
            val egressInfo = response.body()
            logger.info("✅ Successfully started egress for room $stageId:")
            logger.info("   EgressId: ${egressInfo?.egressId}")
            logger.info("   Status: ${egressInfo?.status}")
            logger.info("   StartedAt: ${egressInfo?.startedAt}")
            logger.info("   FileResults: ${egressInfo?.fileResultsList}")
            return egressInfo
        } else {
            logger.error("❌ Failed to start egress for room $stageId:")
            logger.error("   HTTP Status: ${response.code()}")
            logger.error("   Error Message: ${response.message()}")
            logger.error("   Response Body: ${response.errorBody()?.string()}")
            return null
        }
    }

    fun stopEgress(egressId: String): Boolean {
        logger.info("Attempting to stop egress: $egressId")
        val call = livekitEgressService.stopEgress(egressId)
        val response = call.execute()

        if (response.isSuccessful) {
            val egressInfo = response.body()
            logger.info("✅ Successfully stopped egress: $egressId")
            logger.info("   Final Status: ${egressInfo?.status}")
            logger.info("   EndedAt: ${egressInfo?.endedAt}")
            logger.info("   FileResults: ${egressInfo?.fileResultsList}")
            egressInfo?.fileResultsList?.forEach { fileResult ->
                logger.info("   📁 File: ${fileResult.filename} - Size: ${fileResult.size} bytes")
            }
            return true
        } else {
            logger.error("❌ Failed to stop egress $egressId:")
            logger.error("   HTTP Status: ${response.code()}")
            logger.error("   Error Message: ${response.message()}")
            logger.error("   Response Body: ${response.errorBody()?.string()}")
            return false
        }
    }

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

    /**
     * End a room - kicks all participants and closes the room
     */
    fun endRoom(stageId: String): Boolean {
        val call = livekitRoomService.deleteRoom(stageId)
        val response = call.execute()
        
        if (response.isSuccessful) {
            logger.info("Successfully ended room: $stageId")
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
            logger.info("Successfully removed participant $participantIdentity from room: $stageId")
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
            logger.info("Successfully ${if (muted) "muted" else "unmuted"} participant $participantIdentity in room: $stageId")
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


