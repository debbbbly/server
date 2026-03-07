package com.debbly.server.stage.model

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.stage.model.StageModel.StageHostModel
import com.debbly.server.stage.repository.entities.StageEntity
import com.debbly.server.stage.repository.entities.StageHostEntity
import com.debbly.server.stage.repository.entities.StageHostId
import com.debbly.server.stage.repository.entities.CloseReason
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.stage.repository.entities.StageVisibility
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
    val topicId: String? = null,
    val eventId: String? = null,
    val challengeId: String? = null,
    val hosts: List<StageHostModel>,
    val status: StageStatus,
    val createdAt: Instant,
    val openedAt: Instant?,
    val closedAt: Instant?,
    val closeReason: CloseReason? = null,
    val isRecorded: Boolean? = null,
    val visibility: StageVisibility = StageVisibility.PUBLIC
) {
    data class StageHostModel(
        val userId: String,
        val stance: ClaimStance?,
        val visibility: StageVisibility? = null
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
    topicId = this.topicId,
    eventId = this.eventId,
    challengeId = this.challengeId,
    hosts = this.hosts.map { model ->
        StageHostEntity(
            id = StageHostId(
                userId = model.userId,
                stageId = this.stageId
            ),
            stance = model.stance,
            visibility = model.visibility
        )
    },
    createdAt = this.createdAt,
    status = this.status,
    openedAt = this.openedAt,
    closedAt = this.closedAt,
    closeReason = this.closeReason,
    isRecorded = this.isRecorded,
    visibility = this.visibility
)

fun StageEntity.toModel() = StageModel(
    stageId = this.stageId,
    type = this.type,
    title = this.title,
    claimId = this.claimId,
    topicId = this.topicId,
    eventId = this.eventId,
    challengeId = this.challengeId,
    hosts = this.hosts.map { entity ->
        StageHostModel(
            userId = entity.id.userId,
            stance = entity.stance,
            visibility = entity.visibility
        )
    },
    createdAt = this.createdAt,
    status = this.status,
    openedAt = this.openedAt,
    closedAt = this.closedAt,
    closeReason = this.closeReason,
    isRecorded = this.isRecorded,
    visibility = this.visibility
)
