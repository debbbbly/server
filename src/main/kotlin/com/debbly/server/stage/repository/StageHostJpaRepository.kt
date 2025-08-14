package com.debbly.server.stage.repository

import com.debbly.server.stage.repository.entities.StageHostEntity
import com.debbly.server.stage.repository.entities.StageHostId
import org.springframework.data.repository.CrudRepository

interface StageHostJpaRepository : CrudRepository<StageHostEntity, StageHostId> {
    fun findByIdStageId(stageId: String): List<StageHostEntity>
}
