package com.debbly.server.backstage

import com.debbly.server.claim.model.ClaimStance
import java.time.Instant

data class BackstageHost(
    val hostId: String,
    val claimIdToStance: Map<String, ClaimStance>,
    val joinedAt: Instant
)
