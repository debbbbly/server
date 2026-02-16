package com.debbly.server.claim.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ClaimJpaRepository : JpaRepository<ClaimEntity, String> {
    fun findByCategoryIdInAndRemovedFalse(categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.categoryId IN :categoryIds AND c.removed = false")
    fun findByCategoryIdInWithAllData(
        @Param("categoryIds") categoryIds: List<String>,
    ): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.removed = false ORDER BY c.createdAt DESC")
    fun findAllWithAllData(): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.claimId IN :claimIds")
    fun findByClaimIds(
        @Param("claimIds") claimIds: List<String>,
    ): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.removed = false AND c.createdAt >= :since")
    fun findByCreatedAtAfter(
        @Param("since") since: Instant,
    ): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.removed = false AND c.categoryId = :categoryId ORDER BY c.createdAt DESC LIMIT :limit")
    fun findByCategoryIdOrderByCreatedAtDesc(
        @Param("categoryId") categoryId: String,
        @Param("limit") limit: Int,
    ): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.removed = false AND c.topicId IN :topicIds")
    fun findByTopicIdIn(
        @Param("topicIds") topicIds: List<String>,
    ): List<ClaimEntity>

    @Query("SELECT c FROM claims c WHERE c.removed = false AND c.topicId IS NOT NULL AND c.createdAt >= :since")
    fun findByTopicIdNotNullAndCreatedAtAfter(
        @Param("since") since: Instant,
    ): List<ClaimEntity>

    fun findBySlugAndRemovedFalse(slug: String): ClaimEntity?
}
