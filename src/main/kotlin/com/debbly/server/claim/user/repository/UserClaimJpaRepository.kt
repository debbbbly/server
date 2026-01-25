package com.debbly.server.claim.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

interface ClaimStanceCount {
    fun getClaimId(): String

    fun getCount(): Long
}

interface ClaimStanceBreakdown {
    fun getClaimId(): String

    fun getStance(): String

    fun getCount(): Long
}

@Repository
interface UserClaimJpaRepository : JpaRepository<UserClaimStanceEntity, UserClaimStanceId> {
    @Query("SELECT uc FROM users_claims uc JOIN FETCH uc.claim c WHERE uc.id.userId = :userId ORDER BY c.createdAt DESC")
    fun findByIdUserId(userId: String): List<UserClaimStanceEntity>

    @Query("SELECT uc.id.claimId FROM users_claims uc WHERE uc.id.userId IN :userIds")
    fun findClaimIdsByUserIds(userIds: List<String>): List<String>

    // fun findByIdUserIdAndCategoryIdIn(userId: String, categoryIds: List<String>): List<UserClaimStanceEntity>
    fun findByIdUserIdAndIdClaimId(
        userId: String,
        claimId: String,
    ): UserClaimStanceEntity?

    fun deleteByIdUserIdAndIdClaimId(
        userId: String,
        claimId: String,
    )

    @Query("SELECT uc.id.claimId as claimId, COUNT(uc) as count FROM users_claims uc WHERE uc.updatedAt >= :since GROUP BY uc.id.claimId")
    fun countRecentStancesByClaimId(since: Instant): List<ClaimStanceCount>

    @Query(
        "SELECT uc.id.claimId as claimId, uc.stance as stance, COUNT(uc) as count FROM users_claims uc WHERE uc.id.claimId IN :claimIds GROUP BY uc.id.claimId, uc.stance",
    )
    fun countStancesByClaimIds(claimIds: List<String>): List<ClaimStanceBreakdown>
}
