package com.debbly.server.claim.topic.top

import org.springframework.data.repository.CrudRepository

interface TopTopicRedisRepository : CrudRepository<TopTopicWithStats, String> {
    fun findAllByOrderByRankAsc(): List<TopTopicWithStats>
}
