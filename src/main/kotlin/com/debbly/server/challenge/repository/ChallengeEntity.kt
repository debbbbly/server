package com.debbly.server.challenge.repository

import com.debbly.server.claim.model.ClaimStance
import jakarta.persistence.*
import java.time.Instant

enum class ChallengeStatus {
    PENDING, ACCEPTED, CANCELLED
}

@Entity(name = "challenges")
data class ChallengeEntity(
    @Id
    val challengeId: String,
    val claimId: String,
    val hostUserId: String,
    @Enumerated(EnumType.STRING)
    val hostStance: ClaimStance,
    @Enumerated(EnumType.STRING)
    val status: ChallengeStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
)
