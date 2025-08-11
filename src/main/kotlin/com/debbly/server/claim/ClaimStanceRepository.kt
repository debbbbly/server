package com.debbly.server.claim

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClaimStanceRepository : JpaRepository<UserClaimStanceEntity, UserClaimStanceId> {
    fun findByIdUserId(userId: String): List<UserClaimStanceEntity>
}
