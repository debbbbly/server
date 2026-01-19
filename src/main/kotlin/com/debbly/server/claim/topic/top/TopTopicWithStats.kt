package com.debbly.server.claim.topic.top

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed

@RedisHash("topTopic")
data class TopTopicWithStats(
    @Id val topicId: String,
    val categoryId: String,
    val title: String,
    @Indexed val rank: Int,
    val score: Double,
    val claimCount: Int,
    val recentDebates: Int
)
