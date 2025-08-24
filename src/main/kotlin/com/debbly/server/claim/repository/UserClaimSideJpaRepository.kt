package com.debbly.server.claim.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserClaimSideJpaRepository : JpaRepository<UserClaimSideEntity, UserClaimSideId> {
    fun findByIdUserId(userId: String): List<UserClaimSideEntity>
}
