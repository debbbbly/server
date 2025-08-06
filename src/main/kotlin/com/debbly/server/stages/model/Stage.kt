package com.debbly.server.stages.model

import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface StageRepository : CrudRepository<StageEntity, String>

data class StageEntity(
    val stageId: String,
    val type: StageType,
    val claimId: String?,
    val createdAt: Instant,
    val closedAt: Instant?
)

enum class StageType {
    SOLO,
    ONE_ON_ONE
}