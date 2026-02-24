package com.debbly.server.event.repository

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.event.model.EventStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.Instant

@Entity(name = "events")
data class EventEntity(
    @Id
    val eventId: String,
    val claimId: String,
    val hostUserId: String,
    @Enumerated(EnumType.STRING)
    val hostStance: ClaimStance,
    val startTime: Instant,
    @Enumerated(EnumType.STRING)
    val status: EventStatus,
    val description: String?,
    val bannerImageUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val cancelledAt: Instant? = null,
    val signedUpCount: Int = 0,
    val reminderCount: Int = 0
)
