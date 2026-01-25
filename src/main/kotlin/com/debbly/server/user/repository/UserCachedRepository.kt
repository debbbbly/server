package com.debbly.server.user.repository

import com.debbly.server.user.model.UserModel
import com.debbly.server.user.model.toEntity
import com.debbly.server.user.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

@Service
class UserCachedRepository(
    private val userJpaRepository: UserJpaRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    companion object {
        private const val CACHE_NAME = "users"
        private val CACHE_TTL = Duration.ofMinutes(10)
    }

    @Cacheable(value = ["users"], key = "#userId", unless = "#result == null")
    fun getById(userId: String): UserModel = findById(userId) ?: throw NoSuchElementException("User not found")

    @Cacheable(value = ["users"], key = "#userId", unless = "#result == null")
    fun findById(userId: String): UserModel? = userJpaRepository.findById(userId).getOrNull()?.toModel()

    @Cacheable(value = ["usersByExternalId"], key = "#externalId", unless = "#result == null")
    fun findByExternalUserId(externalId: String): UserModel? = userJpaRepository.findByExternalUserId(externalId).getOrNull()?.toModel()

    @Cacheable(value = ["usersByUsername"], key = "#username.toLowerCase()", unless = "#result == null")
    fun findByUsername(username: String): UserModel? = userJpaRepository.findByUsernameNormalized(username.lowercase()).getOrNull()?.toModel()

    @Caching(
        evict = [
            CacheEvict(value = ["users"], key = "#result.userId"),
            CacheEvict(value = ["usersByExternalId"], key = "#result.externalUserId"),
            CacheEvict(value = ["usersByUsername"], key = "#result.usernameNormalized")
        ]
    )
    fun save(user: UserModel): UserModel = userJpaRepository.save(user.toEntity()).toModel()

    fun findTop100ByRankDesc(): List<UserModel> {
        return userJpaRepository.findTop100ByRankDesc(PageRequest.of(0, 100))
            .map { it.toModel() }
    }

    @CacheEvict(value = ["usersByExternalId"], key = "#externalId")
    fun evictByExternalUserId(externalId: String) {
        // Cache eviction only
    }

    fun findByExternalUserIdUncached(externalId: String): UserModel? {
        return userJpaRepository.findByExternalUserId(externalId).getOrNull()?.toModel()
    }

    /**
     * Batch fetch users by IDs using Redis mget.
     * Cache misses are fetched from DB and cached.
     */
    fun findByIds(userIds: List<String>): Map<String, UserModel> {
        if (userIds.isEmpty()) return emptyMap()

        val uniqueIds = userIds.distinct()
        val cacheKeys = uniqueIds.map { "$CACHE_NAME::$it" }

        // Batch fetch from Redis
        val cachedValues = redisTemplate.opsForValue().multiGet(cacheKeys) ?: List(cacheKeys.size) { null }

        val result = mutableMapOf<String, UserModel>()
        val missedIds = mutableListOf<String>()

        // Process cached values
        uniqueIds.forEachIndexed { index, userId ->
            val cached = cachedValues[index]
            if (cached != null && cached is UserModel) {
                result[userId] = cached
            } else {
                missedIds.add(userId)
            }
        }

        // Fetch cache misses from DB
        if (missedIds.isNotEmpty()) {
            val dbUsers = userJpaRepository.findAllByUserIdIn(missedIds)
                .map { it.toModel() }

            // Cache the fetched users
            val toCache = dbUsers.associate { "$CACHE_NAME::${it.userId}" to it as Any }
            if (toCache.isNotEmpty()) {
                redisTemplate.opsForValue().multiSet(toCache)
                // Set TTL for each key
                toCache.keys.forEach { key ->
                    redisTemplate.expire(key, CACHE_TTL)
                }
            }

            // Add to result
            dbUsers.forEach { result[it.userId] = it }
        }

        return result
    }
}
