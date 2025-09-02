package com.debbly.server.match.model

import com.debbly.server.claim.model.ClaimStance
import java.time.Instant

data class Match(
    val matchId: String,
    val claim: MatchClaim,
    val status: MatchStatus,
    val opponents: List<MatchOpponent>,
    val createdAt: Instant,
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

enum class MatchReason {
    COMMON_STANCE_OPPOSITE,   // Users had common claims with opposing stances
    USER_STANCE_ASSIGNED,     // One user had stance, other was assigned opposing stance
    TOP_CLAIM_RANDOM          // Both users assigned random stances on top-scored claim
}