package com.debbly.server.claim.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserClaimStanceJpaRepository : JpaRepository<UserClaimStanceEntity, UserClaimStanceId> {
    fun findByIdUserId(userId: String): List<UserClaimStanceEntity>
}
