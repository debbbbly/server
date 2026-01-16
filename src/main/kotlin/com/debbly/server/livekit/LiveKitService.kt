package com.debbly.server.livekit

import com.debbly.server.IdService
import com.debbly.server.config.LiveKitConfig
import com.debbly.server.settings.SettingsService
import io.livekit.server.*
import livekit.LivekitEgress
import livekit.LivekitEgress.ImageFileSuffix.IMAGE_SUFFIX_INDEX
import livekit.LivekitModels
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LiveKitService(
    private val liveKitConfig: LiveKitConfig,
    private val s3Config: S3LiveKitProperties,
    private val idService: IdService,
    private val livekitRoomService: RoomServiceClient,
    private val livekitEgressService: EgressServiceClient,
    private val settings: SettingsService,
    private val clock: java.time.Clock
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

    fun startCompositeEgress(
        stageId: String,
    ): LivekitEgress.EgressInfo? {
        logger.debug("Starting HLS egress for stage $stageId")

        val s3Upload = LivekitEgress.S3Upload.newBuilder()
            .setEndpoint(s3Config.endpoint)
            .setBucket(s3Config.bucket.egress)
            .setRegion(s3Config.region)
            .setAccessKey(s3Config.accessKey)
            .setSecret(s3Config.secret)
            .setForcePathStyle(s3Config.forcePathStyle)
            .build()

        // HLS Segment Output
        val segmentOutput = LivekitEgress.SegmentedFileOutput.newBuilder()
            .setFilenamePrefix("$stageId/")
            .setPlaylistName("playlist.m3u8")
            .setLivePlaylistName("playlist-live.m3u8")
            .setSegmentDuration(settings.getHlsSegmentDuration())
            .setS3(s3Upload)
            .build()

        val call = livekitEgressService.startRoomCompositeEgress(
            stageId,
            segmentOutput,
            "grid",
            LivekitEgress.EncodingOptionsPreset.H264_1080P_30
        )
        val response = call.execute()

        if (response.isSuccessful) {
            val egressInfo = response.body()
            logger.info("Started HLS egress for room $stageId, egressId: ${egressInfo?.egressId}")
            return egressInfo
        } else {
            logger.error("Failed to start HLS egress for room $stageId: ${response.code()} ${response.message()}")
            return null
        }
    }

    fun startThumbnailEgress(stageId: String) {
        val s3Upload = LivekitEgress.S3Upload.newBuilder()
            .setEndpoint(s3Config.endpoint)
            .setBucket(s3Config.bucket.egress)
            .setRegion(s3Config.region)
            .setAccessKey(s3Config.accessKey)
            .setSecret(s3Config.secret)
            .setForcePathStyle(s3Config.forcePathStyle)
            .build()

        try {
            val imageOutput = LivekitEgress.ImageOutput.newBuilder()
                .setCaptureInterval(60)
                .setWidth(1920)
                .setHeight(1080)
                .setFilenamePrefix("$stageId/thumbnails/")
                .setFilenameSuffix(IMAGE_SUFFIX_INDEX)
                .setDisableManifest(true)
                .setS3(s3Upload)
                .build()

            val imageCall = livekitEgressService.startRoomCompositeEgress(
                stageId,
                imageOutput,
                "grid",
            )

            val imageResponse = imageCall.execute()

            if (imageResponse.isSuccessful) {
                logger.info("Started thumbnail egress for room $stageId, egressId: ${imageResponse.body()?.egressId}")
            } else {
                logger.warn("Failed to start thumbnail egress for room $stageId: ${imageResponse.message()}")
            }
        } catch (e: Exception) {
            logger.error("Error starting thumbnail egress for room $stageId", e)
        }
    }

    data class StopEgressResult(
        val success: Boolean,
        val startedAt: Long?,
        val endedAt: Long?
    )

    fun stopEgress(egressId: String): StopEgressResult {
        val call = livekitEgressService.stopEgress(egressId)
        val response = call.execute()

        if (response.isSuccessful) {
            val egressInfo = response.body()

            // LiveKit timestamps are in nanoseconds, convert to milliseconds
            val startedAt = egressInfo?.startedAt?.takeIf { it > 0 }?.let { it / 1_000_000 }
            val endedAt = egressInfo?.endedAt?.takeIf { it > 0 }?.let { it / 1_000_000 } ?: clock.millis()

            val durationSeconds = if (startedAt != null) (endedAt - startedAt) / 1000 else null
            logger.info("Stopped egress $egressId, duration: ${durationSeconds?.let { "${it}s" } ?: "unknown"}")

            return StopEgressResult(success = true, startedAt = startedAt, endedAt = endedAt)
        } else {
            logger.error("Failed to stop egress $egressId: ${response.code()} ${response.message()}")
            return StopEgressResult(success = false, startedAt = null, endedAt = null)
        }
    }

    data class EgressDetails(
        val egressId: String,
        val status: LivekitEgress.EgressStatus,
        val startedAtMillis: Long,
        val endedAtMillis: Long?,
        val roomName: String?,
        val isRoomComposite: Boolean
    ) {
        val isActive: Boolean
            get() = status == LivekitEgress.EgressStatus.EGRESS_ACTIVE ||
                    status == LivekitEgress.EgressStatus.EGRESS_STARTING
    }

    fun listActiveEgresses(roomName: String? = null): List<EgressDetails> {
        return listAllEgresses(roomName).filter { it.isActive }
    }

    fun countActiveRoomCompositeEgresses(): Int {
        return listActiveEgresses().count { it.isRoomComposite }
    }

    fun listAllEgresses(roomName: String? = null): List<EgressDetails> {
        val call = livekitEgressService.listEgress(roomName, null)
        val response = call.execute()

        if (response.isSuccessful) {
            val egressInfoList = response.body() ?: emptyList()
            logger.debug("Found ${egressInfoList.size} total egresses${roomName?.let { " for room $it" } ?: ""}")
            return egressInfoList.map { it.toEgressDetails() }
        } else {
            logger.error("Failed to list egresses: ${response.code()} ${response.message()}")
            return emptyList()
        }
    }

    private fun LivekitEgress.EgressInfo.toEgressDetails() = EgressDetails(
        egressId = egressId,
        status = status,
        startedAtMillis = startedAt / 1_000_000,
        endedAtMillis = endedAt.takeIf { it > 0 }?.let { it / 1_000_000 },
        roomName = roomName,
        isRoomComposite = hasRoomComposite()
    )

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


