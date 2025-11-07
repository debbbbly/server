package com.debbly.server.match.event

import com.debbly.server.match.model.Match

/**
 * Event published when all opponents accept a match.
 */
data class MatchAcceptedAllEvent(
    val match: Match
)