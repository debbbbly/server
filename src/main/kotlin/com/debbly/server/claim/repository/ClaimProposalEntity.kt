package com.debbly.server.claim.repository

import com.debbly.server.claim.model.ClaimStance
import jakarta.persistence.*
import java.time.Instant

@Entity(name = "claim_proposals")
data class ClaimProposalEntity(
    @Id
    val proposalId: String,
    val claimId: String?,
    val userId: String,
    val originalTitle: String,
    val normalizedTitle: String?,
    val isValid: Boolean,
    val failureReasons: String?,
    val reasoning: String?,
    val categoryId: String?,
    val tags: String?,
    @Enumerated(EnumType.STRING)
    val userStance: ClaimStance?,
    val createdAt: Instant
)