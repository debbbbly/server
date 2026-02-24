package com.debbly.server.event.repository

import com.debbly.server.event.model.EventAcceptanceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventParticipantJpaRepository : JpaRepository<EventParticipantEntity, EventUserId> {
    fun findByEventIdAndUserId(eventId: String, userId: String): EventParticipantEntity?

    fun findByEventIdOrderByCreatedAtAsc(eventId: String): List<EventParticipantEntity>

    fun findByEventIdAndStatusOrderByCreatedAtAsc(eventId: String, status: EventAcceptanceStatus): List<EventParticipantEntity>

    @Query(
        value = """
            SELECT *
            FROM event_participants
            WHERE event_id = :eventId
              AND status = 'SIGNED_UP'
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE
        """,
        nativeQuery = true
    )
    fun findNextSignedUpForUpdate(@Param("eventId") eventId: String): EventParticipantEntity?

    @Query(
        value = """
            SELECT *
            FROM event_participants
            WHERE event_id = :eventId
              AND user_id = :targetUserId
              AND status = 'SIGNED_UP'
            LIMIT 1
            FOR UPDATE
        """,
        nativeQuery = true
    )
    fun findSignedUpByEventAndUserForUpdate(
        @Param("eventId") eventId: String,
        @Param("targetUserId") targetUserId: String
    ): EventParticipantEntity?

    @Query(
        """
        SELECT COUNT(a) FROM event_participants a
        WHERE a.eventId = :eventId
        AND a.status IN :statuses
        """
    )
    fun countByEventIdAndStatuses(
        @Param("eventId") eventId: String,
        @Param("statuses") statuses: List<EventAcceptanceStatus>
    ): Long

    @Query(
        """
        SELECT a.eventId AS eventId, COUNT(a) AS count
        FROM event_participants a
        WHERE a.eventId IN :eventIds
        AND a.status IN :statuses
        GROUP BY a.eventId
        """
    )
    fun countByEventIdsAndStatuses(
        @Param("eventIds") eventIds: List<String>,
        @Param("statuses") statuses: List<EventAcceptanceStatus>
    ): List<EventCountProjection>
}
