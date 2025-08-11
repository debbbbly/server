package com.debbly.server.stage.repository

import com.debbly.server.stage.model.StageEntity
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface StageRepository : CrudRepository<StageEntity, String> {
    @EntityGraph(value = "Stage.withHosts")
    override fun findById(stageId: String): Optional<StageEntity>
}
