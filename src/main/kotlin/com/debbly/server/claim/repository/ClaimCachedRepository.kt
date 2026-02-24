package com.debbly.server.claim.repository

import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.toEntity
import com.debbly.server.claim.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

@Service
class ClaimCachedRepository(
    private val claimJpaRepository: ClaimJpaRepository,
    private val redisTemplate: RedisTemplate<String, Any>,
) {
    companion object {
        private const val CACHE_NAME = "claimsByClaimId"
        private val CACHE_TTL = Duration.ofMinutes(10)
    }

    @Cacheable(value = ["claimsByClaimId"], key = "#claimId")
    fun getById(claimId: String): ClaimModel =
        claimJpaRepository.findById(claimId).getOrNull()?.toModel() ?: throw NoSuchElementException("Claim not found")

    @Cacheable(value = ["claimsByClaimId"], key = "#claimId", unless = "#result == null")
    fun findById(claimId: String): ClaimModel? =
        claimJpaRepository.findById(claimId).getOrNull()?.toModel()

    @Cacheable(value = ["claimsBySlug"], key = "#slug", unless = "#result == null")
    fun findBySlug(slug: String): ClaimModel? =
        claimJpaRepository.findBySlugAndRemovedFalse(slug)?.toModel()

    fun findByIds(claimIds: List<String>): Map<String, ClaimModel> {
        if (claimIds.isEmpty()) return emptyMap()
        val uniqueIds = claimIds.distinct()
        val cacheKeys = uniqueIds.map { "$CACHE_NAME::$it" }

        val cached = redisTemplate.opsForValue().multiGet(cacheKeys) ?: List(cacheKeys.size) { null }

        val result = mutableMapOf<String, ClaimModel>()
        val misses = mutableListOf<String>()
        uniqueIds.forEachIndexed { i, id ->
            val v = cached[i]
            if (v != null && v is ClaimModel) result[id] = v else misses.add(id)
        }

        if (misses.isNotEmpty()) {
            val dbClaims = claimJpaRepository.findByClaimIds(misses).map { it.toModel() }
            val toCache = dbClaims.associate { "$CACHE_NAME::${it.claimId}" to it as Any }
            if (toCache.isNotEmpty()) {
                redisTemplate.opsForValue().multiSet(toCache)
                toCache.keys.forEach { redisTemplate.expire(it, CACHE_TTL) }
            }
            dbClaims.forEach { result[it.claimId] = it }
        }

        return result
    }

    fun search(query: String, categoryId: String?, limit: Int): List<ClaimModel> =
        claimJpaRepository.searchByTitle("%${query.lowercase()}%", categoryId, limit).map { it.toModel() }

    @CacheEvict(value = ["claimsByClaimId"], key = "#claim.claimId")
    fun save(claim: ClaimModel) = claimJpaRepository.save(claim.toEntity()).toModel()

}