package com.debbly.server.stages.model

import org.springframework.data.repository.CrudRepository

interface StageHostRepository : CrudRepository<StageHostEntity, StageHostId> {
    fun findByIdStageId(stageId: String): List<StageHostEntity>
}
