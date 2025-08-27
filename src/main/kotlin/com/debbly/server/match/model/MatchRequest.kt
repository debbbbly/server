package com.debbly.server.match.model

import com.debbly.server.claim.user.ClaimStance
import java.time.Instant

data class MatchRequest(
    val userId: String,
    val claimIdToStance: Map<String, ClaimStance>,
    val skipClaimIds: Collection<String> = emptySet(),
    val joinedAt: Instant
)
