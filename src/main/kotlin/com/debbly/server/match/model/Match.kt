package com.debbly.server.match.model

import com.debbly.server.claim.model.ClaimSide

import java.time.Instant

data class Match(
    val matchId: String,
    val claim: MatchClaim,
    val status: MatchStatus,
    val sides: List<MatchSide>,
    val createdAt: Instant
) {
    data class MatchSide(
        val userId: String,
        val username: String?,
        val avatarUrl: String?,
        val side: ClaimSide?,
        val status: MatchSideStatus
    )

    data class MatchClaim(
        val claimId: String,
        val title: String
    )
}

enum class MatchStatus {
    PENDING, ACCEPTED, REJECTED
}

enum class MatchSideStatus {
    PENDING, ACCEPTED, REJECTED
}