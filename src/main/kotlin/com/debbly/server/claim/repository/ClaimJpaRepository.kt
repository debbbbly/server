package com.debbly.server.claim.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClaimJpaRepository : JpaRepository<ClaimEntity, String> {
    fun findByCategoryCategoryIdIn(categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT DISTINCT c FROM claims c LEFT JOIN FETCH c.category cat LEFT JOIN FETCH c.tags WHERE cat.categoryId IN :categoryIds")
    fun findByCategoryCategoryIdInWithAllData(@Param("categoryIds") categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT c FROM claims c LEFT JOIN FETCH c.category LEFT JOIN FETCH c.tags")
    fun findAllWithAllData(): List<ClaimEntity>
}
