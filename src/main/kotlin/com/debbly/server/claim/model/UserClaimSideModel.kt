package com.debbly.server.claim.model

import java.time.Instant

enum class ClaimSide {
    PRO,
    ANY,
    CON,
}

data class UserClaimSideModel(
    val claimId: String,
    val categoryId: String,
    val userId: String,
    val side: ClaimSide,
    val updatedAt: Instant
)