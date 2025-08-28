package com.debbly.server.claim.user

import com.debbly.server.claim.model.ClaimModel
import java.time.Instant

enum class ClaimStance {
    FOR,
    EITHER,
    AGAINST,
}

data class UserClaimModel(
    val claim: ClaimModel,
    val userId: String,
    val stance: ClaimStance,
    val priority: Int?,
    val updatedAt: Instant
)