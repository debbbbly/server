package com.debbly.server.event.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import java.time.Instant

@Entity(name = "event_reminders")
@IdClass(EventUserId::class)
data class EventReminderEntity(
    @Id
    val eventId: String,
    @Id
    val userId: String,
    val createdAt: Instant
)
