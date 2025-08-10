package com.debbly.server.match

import com.debbly.server.claim.Stance
import java.time.Instant

data class QueueUser(
    val userId: String,
    val stances: Map<String, Stance>,
    val joinedAt: Instant
)
