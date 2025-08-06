package com.debbly.server.debates

data class DebateMatch(
    val id: String,
    val claim: String,
    val side: DebateSide,
    val opponent: OpponentInfo
)

data class OpponentInfo(
    val userId: String,
    val username: String?,
    val avatarUrl: String?,
)
