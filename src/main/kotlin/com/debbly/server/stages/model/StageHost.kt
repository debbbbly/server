package com.debbly.server.stages.model

import org.springframework.data.repository.CrudRepository

interface StageHostRepository : CrudRepository<StageHostEntity, String> {
    fun findByStageIdAndUserId(stageId: String, userId: String): StageHostEntity?
    fun findByStageId(stageId: String): List<StageHostEntity>
}

data class StageHostEntity(
    val stageId: String,
    val userId: String
)