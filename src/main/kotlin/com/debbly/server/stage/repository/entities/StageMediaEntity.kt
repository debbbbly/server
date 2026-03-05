package com.debbly.server.stage.repository.entities

import jakarta.persistence.*
import java.time.Instant

enum class StageMediaStatus { IN_PROGRESS, COMPLETED, FAILED }

enum class StageMediaVisibility { PUBLIC, HOST_ONLY }

@Entity(name = "stage_media")
data class StageMediaEntity(
    @Id val stageId: String,
    val mediaPath: String,
    val thumbnailUrl: String? = null,
    @Enumerated(EnumType.STRING) val status: StageMediaStatus,
    val compositeEgressId: String? = null,
    val portraitCompositeEgressId: String? = null,
    @Enumerated(EnumType.STRING) val visibility: StageMediaVisibility = StageMediaVisibility.PUBLIC,
    val durationSeconds: Long? = null,
    val createdAt: Instant,
) {
    val hlsLiveUrl: String get() = "$mediaPath/video/landscape/playlist-live.m3u8"
    val hlsRecordingUrl: String get() = "$mediaPath/video/landscape/playlist.m3u8"
    val portraitHlsLiveUrl: String get() = "$mediaPath/video/portrait/playlist-live.m3u8"
    val portraitHlsRecordingUrl: String get() = "$mediaPath/video/portrait/playlist.m3u8"
}
