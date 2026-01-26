package com.debbly.server.claim.top

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed

@RedisHash("topClaim")
data class TopClaimWithStats(
    @Id val claimId: String,
    val claimSlug: String?,
    val categoryId: String,
    val topicId: String?,
    val title: String,
    @Indexed val rank: Int,
    val score: Double,
    val recentDebates: Int,
    val forCount: Int,
    val againstCount: Int,
    val recentInterest: Int
)
