package com.debbly.server.event.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventReminderJpaRepository : JpaRepository<EventReminderEntity, EventUserId> {
    fun findByEventIdAndUserId(eventId: String, userId: String): EventReminderEntity?
    fun deleteByEventIdAndUserId(eventId: String, userId: String)

    fun countByEventId(eventId: String): Int

    @Query(
        """
        SELECT r.eventId AS eventId, COUNT(r) AS count
        FROM event_reminders r
        WHERE r.eventId IN :eventIds
        GROUP BY r.eventId
        """
    )
    fun countByEventIds(@Param("eventIds") eventIds: List<String>): List<EventCountProjection>
}
