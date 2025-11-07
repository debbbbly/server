package com.debbly.server.pusher.dto

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.match.model.MatchStatus

/**
 * DTO for Match notifications sent via Pusher.
 * Converts Instant to epoch milliseconds for Gson serialization.
 */
data class MatchNotificationDto(
    val matchId: String,
    val claim: MatchClaimDto,
    val status: MatchStatus,
    val opponents: List<MatchOpponentDto>,
    val ttl: Long,
    val updatedAt: Long  // Epoch milliseconds instead of Instant
) {
    data class MatchOpponentDto(
        val userId: String,
        val username: String?,
        val avatarUrl: String?,
        val stance: ClaimStance?,
        val status: MatchOpponentStatus,
        val ignores: Int,
    )

    data class MatchClaimDto(
        val claimId: String,
        val title: String
    )
}

fun Match.toNotificationDto() = MatchNotificationDto(
    matchId = matchId,
    claim = MatchNotificationDto.MatchClaimDto(
        claimId = claim.claimId,
        title = claim.title
    ),
    status = status,
    opponents = opponents.map { opponent ->
        MatchNotificationDto.MatchOpponentDto(
            userId = opponent.userId,
            username = opponent.username,
            avatarUrl = opponent.avatarUrl,
            stance = opponent.stance,
            status = opponent.status,
            ignores = opponent.ignores
        )
    },
    ttl = ttl,

    // todo update to string
    updatedAt = updatedAt.toEpochMilli()
)
