package com.debbly.server.claim.model

import java.time.Instant

enum class ClaimSide {
    FOR,
    EITHER,
    AGAINST,
}

data class UserClaimSideModel(
    val claimId: String,
    val categoryId: String,
    val userId: String,
    val side: ClaimSide,
    val updatedAt: Instant
)