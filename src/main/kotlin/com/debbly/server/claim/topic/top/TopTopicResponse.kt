package com.debbly.server.claim.topic.top

data class TopTopicResponse(
    val topicId: String,
    val topicSlug: String,
    val categoryId: String,
    val title: String,
    val rank: Int,
    val claimCount: Int,
    val recentDebates: Int
)
