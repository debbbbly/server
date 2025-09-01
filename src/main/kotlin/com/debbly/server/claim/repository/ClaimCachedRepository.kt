package com.debbly.server.claim.repository

import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.toEntity
import com.debbly.server.claim.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class ClaimCachedRepository(
    private val claimJpaRepository: ClaimJpaRepository,
) {

    @Cacheable(value = ["claimsByClaimId"], key = "#claimId")
    fun getById(claimId: String): ClaimModel =
        claimJpaRepository.findById(claimId).getOrNull()?.toModel() ?: throw NoSuchElementException("Claim not found")

    @Cacheable(value = ["claimsByClaimId"], key = "#claimId", unless = "#result == null")
    fun findById(claimId: String): ClaimModel? =
        claimJpaRepository.findById(claimId).getOrNull()?.toModel()

    @Cacheable(value = ["allClaims"])
    fun findAll(): List<ClaimModel> =
        claimJpaRepository.findAllWithAllData().map { it.toModel() }

    @Caching(
        evict = [
            CacheEvict(value = ["claimsByClaimId"], key = "#claim.claimId"),
            CacheEvict(value = ["allClaims"], allEntries = true)
        ]
    )
    fun save(claim: ClaimModel) = claimJpaRepository.save(claim.toEntity()).toModel()

}