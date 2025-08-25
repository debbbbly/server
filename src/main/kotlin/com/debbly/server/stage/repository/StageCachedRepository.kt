package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.toEntity
import com.debbly.server.stage.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class StageCachedRepository(
    private val stageJpaRepository: StageJpaRepository,
) {

    @Cacheable(value = ["stagesByStageId"], key = "#stageId")
    fun getById(stageId: String): StageModel =
        stageJpaRepository.findByIdWithAllData(stageId).getOrNull()?.toModel() ?: throw NoSuchElementException("Stage not found")

    @Cacheable(value = ["stagesByStageId"], key = "#stageId", unless = "#result == null")
    fun findById(stageId: String): StageModel? =
        stageJpaRepository.findByIdWithAllData(stageId).getOrNull()?.toModel()

    @CacheEvict(value = ["stagesByStageId"], key = "#stage.stageId")
    fun save(stage: StageModel) = stageJpaRepository.save(stage.toEntity()).toModel()

    @CacheEvict(value = ["stagesByStageId"], key = "#stageId")
    fun evictById(stageId: String) {
        // This method only evicts the cache entry
    }

    @CacheEvict(value = ["stagesByStageId"], allEntries = true)
    fun evictAll() {
        // This method evicts all cache entries for stages
    }

}