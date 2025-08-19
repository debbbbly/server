package com.debbly.server.backstage

import com.debbly.server.claim.model.ClaimStance

data class Match(
    val matchId: String,
    val claim: MatchClaim,
    val status: MatchStatus,
    val sides: List<MatchSide>,
) {
    data class MatchSide(
        val userId: String,
        val username: String?,
        val avatarUrl: String?,
        val stance: ClaimStance?
    )

    data class MatchClaim(
        val claimId: String,
        val title: String
    )
}

enum class MatchStatus {
    PENDING, ACCEPTED, REJECTED
}