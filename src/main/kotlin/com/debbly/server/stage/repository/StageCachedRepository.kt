package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageEntity
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class StageCachedRepository(
    private val stageRepository: StageRepository,
) {
    @Cacheable(value = ["stages"], key = "#stageId", unless = "#result == null")
    fun findById(stageId: String) = stageRepository.findById(stageId).getOrNull()

    @Cacheable(value = ["stages"], key = "#stageId", unless = "#result == null")
    fun getById(stageId: String): StageEntity = stageRepository.findById(stageId)
        .orElseThrow { Exception("Stage '$stageId' not found") }

    fun save(stage: StageEntity): StageEntity = stageRepository.save(stage)

}