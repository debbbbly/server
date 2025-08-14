package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.StageModel.StageHostModel
import com.debbly.server.stage.repository.entities.StageEntity
import com.debbly.server.stage.repository.entities.StageHostEntity
import com.debbly.server.stage.repository.entities.StageHostId
import org.springframework.stereotype.Service

@Service
class StageRepository(
    private val stageJpaRepository: StageJpaRepository,
) {

    fun getById(stageId: String): StageModel = stageJpaRepository.findById(stageId)
        .orElseThrow { Exception("Stage '$stageId' not found") }
        .toModel()

    fun save(stage: StageModel) =
        stageJpaRepository.save(stage.toEntity()).toModel()

    private fun StageModel.toEntity() = StageEntity(
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

    private fun StageEntity.toModel() = StageModel(
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
}