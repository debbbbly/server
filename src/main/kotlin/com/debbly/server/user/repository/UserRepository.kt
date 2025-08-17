package com.debbly.server.user.repository

import com.debbly.server.user.UserEntity
import org.springframework.stereotype.Service

@Service
class UserRepository(
    private val userCachedRepository: UserCachedRepository,
    private val userJpaRepository: UserJpaRepository
) {
    fun save(user: UserEntity): UserEntity = userJpaRepository.save(user)

    fun getById(userId: String): UserEntity = userCachedRepository.getById(userId)

    fun findById(userId: String): UserEntity? = userCachedRepository.findById(userId)

    fun findByExternalUserId(externalId: String): UserEntity? = userCachedRepository.findByExternalUserId(externalId)

    fun findByUsername(username: String): UserEntity? = userCachedRepository.findByUsername(username)
}
