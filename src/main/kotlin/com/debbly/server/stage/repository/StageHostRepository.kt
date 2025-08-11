package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageHostEntity
import com.debbly.server.stage.model.StageHostId
import org.springframework.data.repository.CrudRepository

interface StageHostRepository : CrudRepository<StageHostEntity, StageHostId> {
    fun findByIdStageId(stageId: String): List<StageHostEntity>
}
