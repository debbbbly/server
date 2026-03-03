package com.debbly.server.stage.repository

import com.debbly.server.stage.repository.entities.StageEntity
import com.debbly.server.stage.repository.entities.StageStatus
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

    // Homepage API queries - fetch stages that are OPEN or have public media (IN_PROGRESS/COMPLETED)
    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.topicId IN :topicIds
        AND (s.status = 'OPEN' OR s.stageId IN (
            SELECT sm.stageId FROM stage_media sm
            WHERE sm.status IN ('IN_PROGRESS', 'COMPLETED')
            AND sm.visibility = 'PUBLIC'
        ))
        ORDER BY s.topicId, s.openedAt DESC NULLS LAST
    """)
    fun findStagesByTopicIds(
        @Param("topicIds") topicIds: List<String>
    ): List<StageEntity>

    // Count stages for a topic that are visible (OPEN or have public media)
    @Query("""
        SELECT COUNT(s) FROM stages s
        WHERE s.topicId = :topicId
        AND (s.status = 'OPEN' OR s.stageId IN (
            SELECT sm.stageId FROM stage_media sm
            WHERE sm.status IN ('IN_PROGRESS', 'COMPLETED')
            AND sm.visibility = 'PUBLIC'
        ))
    """)
    fun countStagesByTopicId(
        @Param("topicId") topicId: String
    ): Int

    // Paginated stages for a single topic with cursor support
    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.topicId = :topicId
        AND (s.status = 'OPEN' OR s.stageId IN (
            SELECT sm.stageId FROM stage_media sm
            WHERE sm.status IN ('IN_PROGRESS', 'COMPLETED')
            AND sm.visibility = 'PUBLIC'
        ))
        AND (:cursorOpenedAt IS NULL OR s.openedAt < :cursorOpenedAt)
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findStagesByTopicIdPaginated(
        @Param("topicId") topicId: String,
        @Param("cursorOpenedAt") cursorOpenedAt: Instant?
    ): List<StageEntity>

    // Recent stages with public media or OPEN, ordered by openedAt DESC (first page)
    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.status = 'OPEN' OR s.stageId IN (
            SELECT sm.stageId FROM stage_media sm
            WHERE sm.status IN ('IN_PROGRESS', 'COMPLETED')
            AND sm.visibility = 'PUBLIC'
        )
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findRecentStages(): List<StageEntity>

    // Recent stages with cursor, ordered by openedAt DESC
    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE (s.status = 'OPEN' OR s.stageId IN (
            SELECT sm.stageId FROM stage_media sm
            WHERE sm.status IN ('IN_PROGRESS', 'COMPLETED')
            AND sm.visibility = 'PUBLIC'
        ))
        AND s.openedAt < :cursorOpenedAt
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findRecentStagesBeforeCursor(
        @Param("cursorOpenedAt") cursorOpenedAt: Instant
    ): List<StageEntity>

    // Fetch stages by claimId ordered by openedAt DESC
    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.claimId = :claimId
        AND s.status IN :statuses
        ORDER BY s.openedAt DESC NULLS LAST
    """)
    fun findStagesByClaimId(
        @Param("claimId") claimId: String,
        @Param("statuses") statuses: List<StageStatus>
    ): List<StageEntity>

    // First page of stages for an event (no cursor), ordered by createdAt DESC
    @Query("""
        SELECT DISTINCT s FROM stages s
        LEFT JOIN FETCH s.hosts
        WHERE s.eventId = :eventId
        ORDER BY s.createdAt DESC
    """)
    fun findByEventId(@Param("eventId") eventId: String): List<StageEntity>

    // Subsequent pages of stages for an event (with cursor), ordered by createdAt DESC
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

    // Only OPEN (live) stages for an event
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
