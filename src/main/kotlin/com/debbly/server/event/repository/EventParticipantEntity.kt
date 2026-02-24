package com.debbly.server.event.repository

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.event.model.EventAcceptanceStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import java.time.Instant

@Entity(name = "event_participants")
@IdClass(EventUserId::class)
data class EventParticipantEntity(
    @Id
    val eventId: String,
    @Id
    val userId: String,
    @Enumerated(EnumType.STRING)
    val status: EventAcceptanceStatus,
    @Enumerated(EnumType.STRING)
    val stance: ClaimStance,
    val createdAt: Instant,
    val updatedAt: Instant
)
