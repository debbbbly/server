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

    @Query("SELECT s.claimId FROM stages s WHERE s.openedAt >= :since AND s.claimId IS NOT NULL")
    fun findClaimIdsByOpenedAtAfter(@Param("since") since: Instant): List<String>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.topicId IN :topicIds
        AND (s.status = 'OPEN' OR (s.status = 'CLOSED' AND s.isRecorded = true AND s.visibility = 'PUBLIC'))
        ORDER BY s.topicId, s.openedAt DESC NULLS LAST
    """)
    fun findStagesByTopicIds(
        @Param("topicIds") topicIds: List<String>
    ): List<StageEntity>

    @Query("""
        SELECT COUNT(s) FROM stages s
        WHERE s.topicId = :topicId
        AND (s.status = 'OPEN' OR (s.status = 'CLOSED' AND s.isRecorded = true AND s.visibility = 'PUBLIC'))
    """)
    fun countStagesByTopicId(
        @Param("topicId") topicId: String
    ): Int

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.topicId = :topicId
        AND (s.status = 'OPEN' OR (s.status = 'CLOSED' AND s.isRecorded = true AND s.visibility = 'PUBLIC'))
        AND (:cursorOpenedAt IS NULL OR s.openedAt < :cursorOpenedAt)
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findStagesByTopicIdPaginated(
        @Param("topicId") topicId: String,
        @Param("cursorOpenedAt") cursorOpenedAt: Instant?
    ): List<StageEntity>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.status = 'OPEN' OR (s.status = 'CLOSED' AND s.isRecorded = true AND s.visibility = 'PUBLIC')
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findRecentStages(): List<StageEntity>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE (s.status = 'OPEN' OR (s.status = 'CLOSED' AND s.isRecorded = true AND s.visibility = 'PUBLIC'))
        AND s.openedAt < :cursorOpenedAt
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findRecentStagesBeforeCursor(
        @Param("cursorOpenedAt") cursorOpenedAt: Instant
    ): List<StageEntity>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.claimId = :claimId
        AND (s.status = 'OPEN' OR (s.status = 'CLOSED' AND s.isRecorded = true AND s.visibility = 'PUBLIC'))
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findStagesByClaimId(
        @Param("claimId") claimId: String
    ): List<StageEntity>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.eventId = :eventId
        ORDER BY s.createdAt DESC
    """)
    fun findByEventId(@Param("eventId") eventId: String): List<StageEntity>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.eventId = :eventId
        AND s.createdAt < :cursor
        ORDER BY s.createdAt DESC
    """)
    fun findByEventIdBeforeCursor(
        @Param("eventId") eventId: String,
        @Param("cursor") cursor: Instant
    ): List<StageEntity>

    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.eventId = :eventId
        AND s.status = 'OPEN'
        ORDER BY s.openedAt DESC
    """)
    fun findOpenByEventId(@Param("eventId") eventId: String): List<StageEntity>

    @Query("SELECT DISTINCT s FROM stages s LEFT JOIN FETCH s.hosts WHERE s.status = 'PENDING' AND s.createdAt < :cutoff")
    fun findPendingCreatedBefore(@Param("cutoff") cutoff: Instant): List<StageEntity>
}
