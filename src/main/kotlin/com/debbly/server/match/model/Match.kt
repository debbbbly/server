package com.debbly.server.match.model

import com.debbly.server.claim.model.ClaimStance
import java.time.Instant

data class Match(
    val matchId: String,
    val claim: MatchClaim,
    val topicId: String? = null,
    val eventId: String? = null,
    val matchReason: MatchReason? = null,
    val status: MatchStatus,
    val opponents: List<MatchOpponent>,
    val ttl: Long,
    val updatedAt: Instant,
) {
    data class MatchOpponent(
        val userId: String,
        val username: String?,
        val avatarUrl: String?,
        val stance: ClaimStance?,
        val status: MatchOpponentStatus,
        val ignores: Int,
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
    CLAIM_MATCH,              // Phase 1: Direct claim match with opposite stances
    TOPIC_MATCH_EXISTING_STANCE, // Phase 2A: Topic match where both users have opposite stances on a claim
    TOPIC_MATCH_DERIVED_STANCE   // Phase 2B: Topic match with stances derived from topic stance + claim's stanceToTopic
}