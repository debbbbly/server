package com.debbly.server.stage.repository

import com.debbly.server.stage.repository.entities.StageEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface StageJpaRepository : CrudRepository<StageEntity, String> {
    @Query("SELECT s FROM stages s LEFT JOIN FETCH s.hosts WHERE s.stageId = :stageId")
    fun findByIdWithAllData(@Param("stageId") stageId: String): Optional<StageEntity>
    
    @Query("SELECT COUNT(s) FROM stages s WHERE s.createdAt >= :since")
    fun countByCreatedAtAfter(since: Instant): Int
    
    @Query("SELECT s.claimId, COUNT(s) FROM stages s WHERE s.createdAt >= :since AND s.claimId IS NOT NULL GROUP BY s.claimId")
    fun countRecentDebatesByClaimId(since: Instant): List<Array<Any>>
    
    @Query("SELECT s.claimId, COUNT(DISTINCT h.id.userId) FROM stages s JOIN s.hosts h WHERE s.claimId IS NOT NULL GROUP BY s.claimId")
    fun countUniqueDebatersByClaimId(): List<Array<Any>>

    @Query("SELECT s FROM stages s LEFT JOIN FETCH s.hosts h WHERE h.id.userId = :userId AND s.createdAt >= :since AND s.openedAt is not null ORDER BY s.createdAt DESC")
    fun findAllByHostUserIdAndCreatedAtAfter(@Param("userId") userId: String, @Param("since") since: Instant): List<StageEntity>
}
