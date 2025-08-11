package com.debbly.server.match

import com.debbly.server.claim.ClaimStance

data class MatchResult(
    val matchId: String,
    val claim: MatchClaim,
    val claimStance: ClaimStance,
    val opponent: Opponent
)

data class MatchClaim(
    val claimId: String,
    val title: String
)

data class Opponent(
    val user: OpponentUser,
    val claimStance: ClaimStance
)

data class OpponentUser(
    val userId: String,
    val username: String?,
    val avatarUrl: String?
)
