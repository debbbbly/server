package com.debbly.server.claim.top

data class TopClaimResponse(
    val claimId: String,
    val claimSlug: String?,
    val categoryId: String,
    val topicId: String?,
    val title: String,
    val rank: Int,
    val recentDebates: Int,
    val forCount: Int,
    val againstCount: Int,
    val recentInterest: Int
)
