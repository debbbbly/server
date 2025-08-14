package com.debbly.server.stage.repository

import com.debbly.server.stage.repository.entities.StageEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.util.Optional

interface StageJpaRepository : CrudRepository<StageEntity, String> {
    @Query("SELECT s FROM stages s LEFT JOIN FETCH s.hosts WHERE s.stageId = :stageId")
    fun findByIdWithAllData(@Param("stageId") stageId: String): Optional<StageEntity>
}
