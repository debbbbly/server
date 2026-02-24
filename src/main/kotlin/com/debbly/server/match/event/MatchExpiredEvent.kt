package com.debbly.server.match.event

import com.debbly.server.match.model.Match

/**
 * Event published when a pending match expires without all opponents accepting.
 */
data class MatchExpiredEvent(
    val match: Match
)
