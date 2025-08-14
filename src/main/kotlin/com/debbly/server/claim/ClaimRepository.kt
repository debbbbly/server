package com.debbly.server.claim

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClaimRepository : JpaRepository<ClaimEntity, String> {
    fun findByCategoriesCategoryIdIn(categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT DISTINCT c FROM claims c LEFT JOIN FETCH c.categories cat LEFT JOIN FETCH c.tags WHERE cat.categoryId IN :categoryIds")
    fun findByCategoriesCategoryIdInWithAllData(@Param("categoryIds") categoryIds: List<String>): List<ClaimEntity>

    @Query("SELECT c FROM claims c LEFT JOIN FETCH c.categories LEFT JOIN FETCH c.tags")
    fun findAllWithAllData(): List<ClaimEntity>
}
