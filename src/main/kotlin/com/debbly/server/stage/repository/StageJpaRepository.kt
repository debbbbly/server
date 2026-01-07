package com.debbly.server.stage.repository

import com.debbly.server.stage.repository.entities.StageEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface ClaimDebateStats {
    fun getClaimId(): String
    fun getCount(): Long
}

interface StageJpaRepository : CrudRepository<StageEntity, String> {
    @Query("SELECT s FROM stages s LEFT JOIN FETCH s.hosts WHERE s.stageId = :stageId")
    fun findByIdWithAllData(@Param("stageId") stageId: String): Optional<StageEntity>

    @Query("SELECT COUNT(s) FROM stages s WHERE s.createdAt >= :since")
    fun countByCreatedAtAfter(since: Instant): Int

    @Query("SELECT s.claimId as claimId, COUNT(s) as count FROM stages s WHERE s.createdAt >= :since AND s.claimId IS NOT NULL GROUP BY s.claimId")
    fun countRecentDebatesByClaimId(since: Instant): List<ClaimDebateStats>

    @Query("SELECT s.claimId as claimId, COUNT(DISTINCT h.id.userId) as count FROM stages s JOIN s.hosts h WHERE s.claimId IS NOT NULL GROUP BY s.claimId")
    fun countUniqueDebatersByClaimId(): List<ClaimDebateStats>

    @Query("SELECT s FROM stages s LEFT JOIN FETCH s.hosts h WHERE h.id.userId = :userId AND s.createdAt >= :since AND s.openedAt is not null ORDER BY s.createdAt DESC")
    fun findAllByHostUserIdAndCreatedAtAfter(@Param("userId") userId: String, @Param("since") since: Instant): List<StageEntity>

    @Query("SELECT DISTINCT s FROM stages s LEFT JOIN FETCH s.hosts WHERE s.stageId IN (SELECT sh.id.stageId FROM stage_hosts sh WHERE sh.id.userId = :userId) ORDER BY s.createdAt DESC LIMIT 10")
    fun findTop10ByHostUserId(@Param("userId") userId: String): List<StageEntity>

    @Query("SELECT DISTINCT s FROM stages s LEFT JOIN FETCH s.hosts WHERE s.recorded = true ORDER BY s.closedAt DESC LIMIT 30")
    fun findTop30RecordedStages(): List<StageEntity>

    @Query("SELECT s.claimId FROM stages s WHERE s.openedAt >= :since AND s.claimId IS NOT NULL")
    fun findClaimIdsByOpenedAtAfter(@Param("since") since: Instant): List<String>
}
