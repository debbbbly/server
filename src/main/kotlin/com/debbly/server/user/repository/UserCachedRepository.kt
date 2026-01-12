package com.debbly.server.user.repository

import com.debbly.server.user.UserEntity
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.model.toEntity
import com.debbly.server.user.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class UserCachedRepository(
    private val userJpaRepository: UserJpaRepository
) {

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

}
