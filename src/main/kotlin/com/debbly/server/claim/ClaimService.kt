package com.debbly.server.claim

import org.springframework.stereotype.Service

@Service
class ClaimService(private val repository: ClaimRepository) {

    fun findAll(): List<ClaimEntity> = repository.findAllWithAllData()

    fun save(claim: ClaimEntity): ClaimEntity = repository.save(claim)

    fun getTopClaims(categoryIds: List<String>?, limit: Int): List<ClaimEntity> {
        return (if (categoryIds.isNullOrEmpty()) {
            repository.findAllWithAllData().take(limit)
        } else {
            repository.findByCategoryCategoryIdInWithAllData(categoryIds).take(limit)
        }).filter { claim -> claim.category.active }
    }
}