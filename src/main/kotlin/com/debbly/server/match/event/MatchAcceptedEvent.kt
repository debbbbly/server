package com.debbly.server.match.event

import com.debbly.server.match.model.Match

/**
 * Event published when an opponent accepts a match (partial acceptance).
 */
data class MatchAcceptedEvent(
    val match: Match,
    val acceptedByUserId: String
)