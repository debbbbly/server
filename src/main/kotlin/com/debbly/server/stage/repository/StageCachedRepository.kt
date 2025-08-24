package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.toModel
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class StageCachedRepository(
    private val stageJpaRepository: StageJpaRepository,
) {

    @Cacheable(value = ["stagesByStageId"], key = "#stageId")
    fun getById(stageId: String): StageModel =
        stageJpaRepository.findById(stageId).getOrNull()?.toModel() ?: throw NoSuchElementException("User not found")
}