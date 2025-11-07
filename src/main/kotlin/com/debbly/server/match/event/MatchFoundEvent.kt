package com.debbly.server.match.event

import com.debbly.server.match.model.Match

/**
 * Event published when a new match is found.
 */
data class MatchFoundEvent(
    val match: Match
)