package com.debbly.server.livekit.egress

import com.debbly.server.config.EgressLayout
import com.debbly.server.config.LiveKitConfig
import com.debbly.server.livekit.S3LiveKitProperties
import com.debbly.server.settings.SettingsService
import com.debbly.server.stage.repository.StageMediaJpaRepository
import io.livekit.server.EgressServiceClient
import livekit.LivekitEgress
import livekit.LivekitEgress.ImageFileSuffix.IMAGE_SUFFIX_INDEX
import org.springframework.beans.factory.annotation.Qualifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

@Service
class EgressService(
    private val s3Config: S3LiveKitProperties,
    private val livekitEgressService: EgressServiceClient,
    private val settings: SettingsService,
    private val clock: java.time.Clock,
    private val liveKitConfig: LiveKitConfig,
    @Qualifier("s3LiveKitClient") private val s3Client: S3Client,
    private val stageMediaRepository: StageMediaJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val thumbnailScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()

    private fun getLayoutUrl(layout: EgressLayout): String {
        val layouts = liveKitConfig.egress.layouts
        return when (layout) {
            EgressLayout.LANDSCAPE -> layouts.landscape
            EgressLayout.PORTRAIT -> layouts.portrait
        } ?: "grid"
    }

    fun startCompositeEgress(
        stageId: String,
        layout: EgressLayout = EgressLayout.LANDSCAPE
    ): LivekitEgress.EgressInfo? {
        val layoutUrl = getLayoutUrl(layout)
        logger.debug("Starting HLS egress for stage $stageId with layout: $layout")

        val s3Upload = buildS3Upload()

        val filenamePrefix = when (layout) {
            EgressLayout.LANDSCAPE -> "$stageId/"
            EgressLayout.PORTRAIT -> "$stageId/portrait/"
        }

        // HLS Segment Output
        val segmentOutput = LivekitEgress.SegmentedFileOutput.newBuilder()
            .setFilenamePrefix(filenamePrefix)
            .setPlaylistName("playlist.m3u8")
            .setLivePlaylistName("playlist-live.m3u8")
            .setSegmentDuration(settings.getHlsSegmentDuration())
            .setS3(s3Upload)
            .build()

        val preset = when (layout) {
            EgressLayout.LANDSCAPE -> LivekitEgress.EncodingOptionsPreset.H264_1080P_30
            EgressLayout.PORTRAIT -> LivekitEgress.EncodingOptionsPreset.PORTRAIT_H264_1080P_30
        }

        val call = livekitEgressService.startRoomCompositeEgress(
            roomName = stageId,
            output = segmentOutput,
            layout = "",
            optionsPreset = preset,
            customBaseUrl = layoutUrl
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

    fun startThumbnailEgress(stageId: String, layout: EgressLayout = EgressLayout.LANDSCAPE) {
        val layoutUrl = getLayoutUrl(layout)
        val (width, height) = when (layout) {
            EgressLayout.LANDSCAPE -> 480 to 270
            EgressLayout.PORTRAIT -> 270 to 480
        }
        val s3Upload = buildS3Upload()

        try {
            val imageOutput = LivekitEgress.ImageOutput.newBuilder()
                .setCaptureInterval(15)
                .setWidth(width)
                .setHeight(height)
                .setFilenamePrefix("$stageId/thumbnails/")
                .setFilenameSuffix(IMAGE_SUFFIX_INDEX)
                .setDisableManifest(true)
                .setS3(s3Upload)
                .build()

            val imageCall = livekitEgressService.startRoomCompositeEgress(
                stageId,
                imageOutput,
                layout = "",
                customBaseUrl = layoutUrl,
            )

            val imageResponse = imageCall.execute()

            if (imageResponse.isSuccessful) {
                val egressId = imageResponse.body()?.egressId
                logger.info("Started thumbnail egress for room $stageId, egressId: $egressId")
                if (egressId != null) {
                    scheduleThumbnailStop(egressId, stageId)
                }
            } else {
                logger.warn("Failed to start thumbnail egress for room $stageId: ${imageResponse.message()}")
            }
        } catch (e: Exception) {
            logger.error("Error starting thumbnail egress for room $stageId", e)
        }
    }

    private fun scheduleThumbnailStop(egressId: String, stageId: String) {
        thumbnailScheduler.schedule({
            try {
                val call = livekitEgressService.stopEgress(egressId)
                val response = call.execute()
                if (response.isSuccessful) {
                    logger.info("Auto-stopped thumbnail egress $egressId for room $stageId")
                    resolveThumbnailUrl(stageId)
                } else {
                    logger.warn("Failed to auto-stop thumbnail egress $egressId: ${response.message()}")
                }
            } catch (e: Exception) {
                logger.error("Error auto-stopping thumbnail egress $egressId", e)
            }
        }, 60, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun resolveThumbnailUrl(stageId: String) {
        try {
            val prefix = "$stageId/thumbnails/"
            val request = ListObjectsV2Request.builder()
                .bucket(s3Config.bucket.egress)
                .prefix(prefix)
                .build()

            val response = s3Client.listObjectsV2(request)
            val jpegKeys = response.contents()
                .map { it.key() }
                .filter { it.endsWith(".jpeg") || it.endsWith(".jpg") }
                .sorted()

            val thumbnailKey = if (jpegKeys.size >= 2) jpegKeys[1] else jpegKeys.firstOrNull()

            if (thumbnailKey != null) {
                val thumbnailUrl = "${s3Config.endpoint}/${s3Config.bucket.egress}/$thumbnailKey"
                val media = stageMediaRepository.findById(stageId).orElse(null)
                if (media != null) {
                    stageMediaRepository.save(media.copy(thumbnailUrl = thumbnailUrl))
                    logger.info("Resolved thumbnail for stage $stageId: $thumbnailUrl")
                }
            } else {
                logger.warn("No thumbnail images found in S3 for stage $stageId")
            }
        } catch (e: Exception) {
            logger.error("Error resolving thumbnail URL for stage $stageId", e)
        }
    }

    data class StopEgressResult(
        val success: Boolean,
        val startedAt: Long?,
        val endedAt: Long?
    )

    fun stopCompositeEgress(egressId: String): StopEgressResult {
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
        return listActiveEgresses().filter { it.isRoomComposite }.mapNotNull { it.roomName }.distinct().size
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

    private fun buildS3Upload(): LivekitEgress.S3Upload {
        return LivekitEgress.S3Upload.newBuilder()
            .setEndpoint(s3Config.endpoint)
            .setBucket(s3Config.bucket.egress)
            .setRegion(s3Config.region)
            .setAccessKey(s3Config.accessKey)
            .setSecret(s3Config.secret)
            .setForcePathStyle(s3Config.forcePathStyle)
            .build()
    }
}
