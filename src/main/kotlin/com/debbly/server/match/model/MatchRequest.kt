package com.debbly.server.match.model

import java.time.Instant

data class MatchRequest(
    val userId: String,
    val claims: List<ClaimWithStance> = emptyList(),
    val topics: List<TopicWithStance> = emptyList(),
    val skipUserIds: Set<String> = emptySet(),
    val skipClaimIds: Set<String> = emptySet(),
    val joinedAt: Instant,
    val ignores: Int = 0
) {
    fun hasOnlyClaims(): Boolean = claims.isNotEmpty() && topics.isEmpty()
    fun hasTopics(): Boolean = topics.isNotEmpty()
}
