package com.debbly.server.claim.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ClaimJpaRepository : JpaRepository<ClaimEntity, String> {
    fun findByCategoryIdIn(categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT DISTINCT c FROM claims c WHERE c.categoryId IN :categoryIds")
    fun findByCategoryIdInWithAllData(@Param("categoryIds") categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT c FROM claims c ORDER BY c.scoreTotal DESC, c.createdAt DESC")
    fun findAllWithAllData(): List<ClaimEntity>

    @Query("SELECT DISTINCT c FROM claims c WHERE c.claimId IN :claimIds")
    fun findByClaimIdInWithAllData(@Param("claimIds") claimIds: List<String>): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.createdAt >= :since")
    fun findByCreatedAtAfter(@Param("since") since: Instant): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.categoryId = :categoryId ORDER BY c.createdAt DESC LIMIT :limit")
    fun findByCategoryIdOrderByCreatedAtDesc(
        @Param("categoryId") categoryId: String,
        @Param("limit") limit: Int
    ): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.topicId IN :topicIds")
    fun findByTopicIdIn(@Param("topicIds") topicIds: List<String>): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.topicId IS NOT NULL AND c.createdAt >= :since")
    fun findByTopicIdNotNullAndCreatedAtAfter(@Param("since") since: Instant): List<ClaimEntity>
}
