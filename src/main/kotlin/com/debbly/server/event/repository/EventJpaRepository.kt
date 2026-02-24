package com.debbly.server.event.repository

import com.debbly.server.event.model.EventStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface EventJpaRepository : JpaRepository<EventEntity, String> {

    @Query(
        """
        SELECT e FROM events e
        WHERE e.status = :status
        AND e.startTime > :cursor
        ORDER BY e.startTime ASC
        """
    )
    fun findUpcomingAfter(
        @Param("status") status: EventStatus,
        @Param("cursor") cursor: Instant
    ): List<EventEntity>

    @Query(
        """
        SELECT e FROM events e
        WHERE e.status = :status
        AND e.startTime < :cursor
        ORDER BY e.startTime DESC
        """
    )
    fun findLiveBefore(
        @Param("status") status: EventStatus,
        @Param("cursor") cursor: Instant
    ): List<EventEntity>

    @Query(
        """
        SELECT e FROM events e
        WHERE e.status IN :statuses
        AND e.startTime < :cursor
        ORDER BY e.startTime DESC
        """
    )
    fun findPastBefore(
        @Param("statuses") statuses: List<EventStatus>,
        @Param("cursor") cursor: Instant
    ): List<EventEntity>

    @Modifying
    @Query("UPDATE events e SET e.signedUpCount = e.signedUpCount + 1 WHERE e.eventId = :eventId")
    fun incrementSignedUpCount(@Param("eventId") eventId: String)

    @Modifying
    @Query("UPDATE events e SET e.signedUpCount = e.signedUpCount - 1 WHERE e.eventId = :eventId AND e.signedUpCount > 1")
    fun decrementSignedUpCount(@Param("eventId") eventId: String)

    @Modifying
    @Query("UPDATE events e SET e.reminderCount = e.reminderCount + 1 WHERE e.eventId = :eventId")
    fun incrementReminderCount(@Param("eventId") eventId: String)

    @Modifying
    @Query("UPDATE events e SET e.reminderCount = e.reminderCount - 1 WHERE e.eventId = :eventId AND e.reminderCount > 1")
    fun decrementReminderCount(@Param("eventId") eventId: String)
}
