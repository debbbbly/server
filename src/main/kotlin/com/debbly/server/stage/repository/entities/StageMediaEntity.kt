package com.debbly.server.stage.repository.entities

import jakarta.persistence.*
import java.time.Instant

enum class StageMediaStatus { IN_PROGRESS, RECORDED, FAILED, NOT_RECORDED }

@Entity(name = "stage_media")
data class StageMediaEntity(
    @Id val stageId: String,
    val path: String,
    val thumbnailUrl: String? = null,
    @Enumerated(EnumType.STRING) val status: StageMediaStatus,
    val hlsLandscapeEgressId: String? = null,
    val hlsPortraitEgressId: String? = null,
    val durationSeconds: Long? = null,
    val createdAt: Instant,
) {
    val hlsLiveLandscapeUrl: String get() = "$path/video/landscape/playlist-live.m3u8"
    val hlsLandscapeUrl: String get() = "$path/video/landscape/playlist.m3u8"
    val hlsLivePortraitUrl: String get() = "$path/video/portrait/playlist-live.m3u8"
    val hlsPortraitUrl: String get() = "$path/video/portrait/playlist.m3u8"
}
