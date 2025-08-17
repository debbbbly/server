package com.debbly.server.stage.repository

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class StageCachedRepository(
    private val stageJpaRepository: StageJpaRepository,
) {

    @Cacheable(value = ["stagesByStageId"], key = "#stageId")
    fun getById(stageId: String) = stageJpaRepository
        .findById(stageId).getOrNull() ?: throw NoSuchElementException("User not found")

}