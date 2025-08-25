package com.debbly.server.livekit

import com.debbly.server.IdService
import com.debbly.server.LiveKitConfig
import io.livekit.server.*
import livekit.LivekitModels
import livekit.LivekitEgress
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LiveKitService(
    private val liveKitConfig: LiveKitConfig,
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
        val call = livekitRoomService.listParticipants(stageId)
        val response = call.execute()
        
        if (response.isSuccessful) {
            val listParticipantsResponse = response.body()
            val participantsList = listParticipantsResponse ?: emptyList()
            logger.info("Retrieved ${participantsList.size} participants for room: $stageId")
            return participantsList
        } else {
            logger.error("Failed to get participants for room $stageId: ${response.code()} ${response.message()}")
            return emptyList()
        }
    }

    fun startRoomEgress(
        stageId: String, 
        s3Bucket: String, 
        s3Key: String, 
        s3Region: String = "us-west-2",
        width: Int = 1920, 
        height: Int = 1080
    ): LivekitEgress.EgressInfo? {
        val s3Upload = LivekitEgress.S3Upload.newBuilder()
            .setBucket(s3Bucket)
            .setRegion(s3Region)
            .setAccessKey(System.getenv("AWS_ACCESS_KEY_ID") ?: "")
            .setSecret(System.getenv("AWS_SECRET_ACCESS_KEY") ?: "")
            .build()

        val encodedFileOutput = LivekitEgress.EncodedFileOutput.newBuilder()
            .setFileType(LivekitEgress.EncodedFileType.MP4)
            .setFilepath("$s3Key.mp4")
            .setS3(s3Upload)
            .build()

        val call = livekitEgressService.startRoomCompositeEgress(
            stageId,
            encodedFileOutput,
            "grid",
            null,
            null,
            false,
            false,
            ""
        )
        val response = call.execute()
        
        if (response.isSuccessful) {
            val egressInfo = response.body()
            logger.info("Started egress for room $stageId: ${egressInfo?.egressId}")
            return egressInfo
        } else {
            logger.error("Failed to start egress for room $stageId: ${response.code()} ${response.message()}")
            return null
        }
    }

    fun stopEgress(egressId: String): Boolean {
        val call = livekitEgressService.stopEgress(egressId)
        val response = call.execute()
        
        if (response.isSuccessful) {
            logger.info("Stopped egress: $egressId")
            return true
        } else {
            logger.error("Failed to stop egress $egressId: ${response.code()} ${response.message()}")
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



}


