package com.debbly.server.claim.model

import java.time.Instant

enum class ClaimStance {
    PRO,
    ANY,
    CON,
}

data class UserClaimStanceModel(
    val claimId: String,
    val categoryId: String,
    val userId: String,
    val stance: ClaimStance,
    val updatedAt: Instant
)