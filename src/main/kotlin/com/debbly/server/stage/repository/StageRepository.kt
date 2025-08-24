package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.toEntity
import com.debbly.server.stage.model.toModel
import org.springframework.stereotype.Service

@Service
class StageRepository(
    private val stageCachedRepository: StageCachedRepository,
    private val stageJpaRepository: StageJpaRepository
) {

    fun getById(stageId: String): StageModel {
        return stageCachedRepository.getById(stageId)
    }

    fun save(stage: StageModel) = stageJpaRepository.save(stage.toEntity()).toModel()

}