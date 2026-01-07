package com.debbly.server.claim.top

import org.springframework.data.repository.CrudRepository

interface TopClaimRedisRepository : CrudRepository<TopClaimWithStats, String> {
    fun findAllByOrderByRankAsc(): List<TopClaimWithStats>
}
