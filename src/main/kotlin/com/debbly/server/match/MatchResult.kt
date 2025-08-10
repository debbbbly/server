package com.debbly.server.match

import com.debbly.server.claim.Stance

data class MatchResult(
    val matchId: String,
    val claim: MatchClaim,
    val stance: Stance,
    val opponent: Opponent
)

data class MatchClaim(
    val claimId: String,
    val title: String
)

data class Opponent(
    val user: OpponentUser,
    val stance: Stance
)

data class OpponentUser(
    val userId: String,
    val username: String?,
    val avatarUrl: String?
)
