package com.debbly.server.match

import com.debbly.server.claim.ClaimStance
import java.time.Instant

data class QueueUser(
    val userId: String,
    val stances: Map<String, ClaimStance>,
    val joinedAt: Instant
)
