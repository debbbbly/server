package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.repository.entities.StageEntity
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class StageCachedRepository(
    private val stageRepository: StageRepository,
) {

    @Cacheable(value = ["stagesByStageId"], key = "#stageId")
    fun getById(stageId: String): StageModel = stageRepository.getById(stageId)

    fun save(stage: StageModel) = stageRepository.save(stage)
}