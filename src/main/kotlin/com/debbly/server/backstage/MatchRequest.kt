package com.debbly.server.backstage

import com.debbly.server.claim.model.ClaimStance
import java.time.Instant

data class MatchRequest(
    val userId: String,
    val claimIdToStance: Map<String, ClaimStance>,
    val skipClaimIds: Collection<String> = emptySet(),
    val joinedAt: Instant
)
