package com.debbly.server.backstage.model

import com.debbly.server.claim.model.ClaimStance

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
        val stance: ClaimStance?,
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