package com.debbly.server.claim.user

import java.time.Instant

enum class ClaimStance {
    FOR,
    EITHER,
    AGAINST,
}

data class UserClaimModel(
    val claimId: String,
    val categoryId: String,
    val userId: String,
    val stance: ClaimStance,
    val priority: Int?,
    val updatedAt: Instant
)