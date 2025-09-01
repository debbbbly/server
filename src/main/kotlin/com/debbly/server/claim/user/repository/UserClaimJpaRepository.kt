package com.debbly.server.claim.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UserClaimJpaRepository : JpaRepository<UserClaimEntity, UserClaimId> {
    @Query("SELECT uc FROM users_claims uc JOIN FETCH uc.claim c WHERE uc.id.userId = :userId ORDER BY c.scoreTotal DESC, c.createdAt DESC")
    fun findByIdUserId(userId: String): List<UserClaimEntity>
    
    // fun findByIdUserIdAndCategoryIdIn(userId: String, categoryIds: List<String>): List<UserClaimEntity>
    fun findByIdUserIdAndIdClaimId(userId: String, claimId: String): UserClaimEntity?
    fun deleteByIdUserIdAndIdClaimId(userId: String, claimId: String)
    
//    @Query("SELECT COUNT(uc) FROM users_claims uc WHERE uc.updatedAt >= :since")
//    fun countByUpdatedAtAfter(since: Instant): Int
    
    @Query("SELECT uc.id.claimId, COUNT(uc) FROM users_claims uc WHERE uc.updatedAt >= :since GROUP BY uc.id.claimId")
    fun countRecentStancesByClaimId(since: Instant): List<Array<Any>>
}
