package com.debbly.server.stage.model

import com.debbly.server.claim.model.ClaimSide
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
        val side: ClaimSide?
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
            side = model.side
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
            side = entity.side
        )
    },
    createdAt = this.createdAt,
    closedAt = this.closedAt,
)