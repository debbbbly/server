package com.debbly.server.stage.model

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.stage.model.StageModel.StageHostModel
import com.debbly.server.stage.repository.entities.StageEntity
import com.debbly.server.stage.repository.entities.StageHostEntity
import com.debbly.server.stage.repository.entities.StageHostId
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class StageModel(
    val stageId: String,
    val type: StageType,
    val title: String?,
    val claimId: String?,
    val hosts: List<StageHostModel>,
    val createdAt: Instant,
    val closedAt: Instant? = null
) {
    data class StageHostModel(
        val userId: String,
        val stance: ClaimStance?
    )
}

enum class StageType {
    SOLO,
    ONE_ON_ONE
}

fun StageModel.toEntity() = StageEntity(
    stageId = this.stageId,
    type = this.type,
    title = this.title,
    claimId = this.claimId,
    hosts = this.hosts.map { model ->
        StageHostEntity(
            id = StageHostId(
                userId = model.userId,
                stageId = this.stageId
            ),
            stance = model.stance
        )
    },
    createdAt = this.createdAt,
    closedAt = this.closedAt,
)

fun StageEntity.toModel() = StageModel(
    stageId = this.stageId,
    type = this.type,
    title = this.title,
    claimId = this.claimId,
    hosts = this.hosts.map { entity ->
        StageHostModel(
            userId = entity.id.userId,
            stance = entity.stance
        )
    },
    createdAt = this.createdAt,
    closedAt = this.closedAt,
)