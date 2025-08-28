package com.debbly.server.claim.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserClaimJpaRepository : JpaRepository<UserClaimEntity, UserClaimId> {
    fun findByIdUserId(userId: String): List<UserClaimEntity>
    fun findByIdUserIdAndCategoryIdIn(userId: String, categoryIds: List<String>): List<UserClaimEntity>
    fun findByIdUserIdAndIdClaimId(userId: String, claimId: String): UserClaimEntity?
    fun deleteByIdUserIdAndIdClaimId(userId: String, claimId: String)
}
