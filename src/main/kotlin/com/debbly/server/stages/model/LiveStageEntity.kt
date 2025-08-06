package com.debbly.server.stages.model

import jakarta.persistence.Entity
import java.time.Instant

@Entity(name = "live_stages")
data class LiveStageEntity(
    val stageId: String,
    val heartbeatAt: Instant
)