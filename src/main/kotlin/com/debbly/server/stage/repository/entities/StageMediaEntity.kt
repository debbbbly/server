package com.debbly.server.stage.repository.entities

import jakarta.persistence.*
import java.time.Instant

enum class StageMediaStatus { IN_PROGRESS, COMPLETED, FAILED }
enum class StageMediaVisibility { PUBLIC, HOST_ONLY }

@Entity(name = "stage_media")
data class StageMediaEntity(
    @Id val stageId: String,
    val hlsLiveUrl: String?,
    val hlsRecordingUrl: String?,
    val thumbnailUrl: String?,
    @Enumerated(EnumType.STRING) val status: StageMediaStatus,
    val compositeEgressId: String,
    val portraitHlsLiveUrl: String? = null,
    val portraitHlsRecordingUrl: String? = null,
    val portraitCompositeEgressId: String? = null,
    @Enumerated(EnumType.STRING) val visibility: StageMediaVisibility = StageMediaVisibility.PUBLIC,
    val durationSeconds: Long? = null,
    val createdAt: Instant
)
