package com.debbly.server.stages.model

import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface LiveStageRepository : CrudRepository<LiveStageEntity, String>

data class LiveStageEntity(
    val stageId: String,
    val heartbeatAt: Instant
)