package com.debbly.server.match.model

import java.time.Instant

data class MatchRequest(
    val userId: String,
    val claims: List<ClaimWithStance> = emptyList(),
    val topics: List<TopicWithStance> = emptyList(),
    val skipUserIds: Set<String> = emptySet(),
    val skipClaimIds: Set<String> = emptySet(),
    val joinedAt: Instant,
    val updatedAt: Instant = joinedAt,
    val ignores: Int = 0,
    val skipCount: Int = 0,
    val status: QueueStatus = QueueStatus.ACTIVE,
    val eventId: String? = null,
    val withUserId: String? = null,
    val challengeId: String? = null,
) {
    fun hasOnlyClaims(): Boolean = claims.isNotEmpty() && topics.isEmpty()
    fun hasTopics(): Boolean = topics.isNotEmpty()
}
