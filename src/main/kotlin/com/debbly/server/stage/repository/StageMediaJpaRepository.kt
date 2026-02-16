package com.debbly.server.stage.repository

import com.debbly.server.stage.repository.entities.StageMediaEntity
import org.springframework.data.repository.CrudRepository

interface StageMediaJpaRepository : CrudRepository<StageMediaEntity, String> {
    fun findByStageIdIn(stageIds: List<String>): List<StageMediaEntity>
}
