package com.debbly.server.stages.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.Instant

@Entity(name = "stages")
data class StageEntity(
    @Id
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