package com.debbly.server.match.model

import com.debbly.server.claim.model.ClaimSide
import java.time.Instant

data class MatchRequest(
    val userId: String,
    val claimIdToSide: Map<String, ClaimSide>,
    val skipClaimIds: Collection<String> = emptySet(),
    val joinedAt: Instant
)
