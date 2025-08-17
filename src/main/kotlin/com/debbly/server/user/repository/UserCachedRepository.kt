package com.debbly.server.user.repository

import com.debbly.server.user.UserEntity
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class UserCachedRepository(
    private val userJpaRepository: UserJpaRepository
) {

    @Cacheable(value = ["users"], key = "#userId", unless = "#result == null")
    fun getById(userId: String): UserEntity = findById(userId) ?: throw NoSuchElementException("User not found")

    @Cacheable(value = ["users"], key = "#userId", unless = "#result == null")
    fun findById(userId: String): UserEntity? = userJpaRepository.findById(userId).getOrNull()

    @Cacheable(value = ["usersByExternalId"], key = "#externalId", unless = "#result == null")
    fun findByExternalUserId(externalId: String): UserEntity? = userJpaRepository.findByExternalUserId(externalId).getOrNull()

    @Cacheable(value = ["usersByUsername"], key = "#username", unless = "#result == null")
    fun findByUsername(username: String): UserEntity? = userJpaRepository.findByUsername(username).getOrNull()

}
