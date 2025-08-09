package com.debbly.server.claim

import org.springframework.stereotype.Service

@Service
class ClaimService(private val repository: ClaimRepository) {

    fun findAll(): List<ClaimEntity> = repository.findAll()

    fun save(claim: ClaimEntity): ClaimEntity = repository.save(claim)

    fun getTopClaims(categoryIds: List<String>?, limit: Int): List<ClaimEntity> {
        return (if (categoryIds.isNullOrEmpty()) {
            repository.findAll().take(limit)
        } else {
            categoryIds.flatMap {
                repository.findByCategoriesCategoryIdIn(listOf(it)).take(limit)
            }
        }).filter { claim -> claim.categories.any { it.active } }
    }
}