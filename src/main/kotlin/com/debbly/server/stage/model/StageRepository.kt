package com.debbly.server.stage.model

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface StageRepository : CrudRepository<StageEntity, String> {
    @EntityGraph(value = "Stage.withHosts")
    override fun findById(id: String): Optional<StageEntity>
}
