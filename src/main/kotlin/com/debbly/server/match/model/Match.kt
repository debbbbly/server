package com.debbly.server.match.model

import com.debbly.server.claim.user.ClaimStance

import java.time.Instant

data class Match(
    val matchId: String,
    val claim: MatchClaim,
    val status: MatchStatus,
    val opponents: List<MatchOpponent>,
    val createdAt: Instant
) {
    data class MatchOpponent(
        val userId: String,
        val username: String?,
        val avatarUrl: String?,
        val stance: ClaimStance?,
        val status: MatchOpponentStatus
    )

    data class MatchClaim(
        val claimId: String,
        val title: String
    )
}

enum class MatchStatus {
    PENDING, ACCEPTED, REJECTED
}

enum class MatchOpponentStatus {
    PENDING, ACCEPTED, REJECTED
}