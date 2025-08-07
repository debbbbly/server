package com.debbly.server.user

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class UserService(private val repository: UserRepository) {

    fun create(user: UserEntity): UserEntity = repository.save(user)

    fun getById(userId: String): UserEntity = findById(userId) ?: throw NoSuchElementException("User not found")

    @Cacheable(value = ["users"], key = "#userId", unless = "#result == null")
    fun findById(userId: String): UserEntity? = repository.findById(userId).getOrNull()

    @Cacheable(value = ["usersByExternalId"], key = "#externalId", unless = "#result == null")
    fun findByExternalUserId(externalId: String): UserEntity? = repository.findByExternalUserId(externalId).getOrNull()

//    fun complete(
//        userId: String,
//        username: String,
//        birthdate: LocalDate
//    ) {
//        if (repository.findByUsername(username).isPresent) {
//            throw IllegalArgumentException("Username already taken")
//        }
//
//        val user = repository.findById(userId)
//            .orElseThrow { NoSuchElementException("User not found") }
//
//        user.username = username
//        user.birthdate = birthdate
//
//        repository.save(user)
//    }

    @Cacheable(value = ["usersByUsername"], key = "#username", unless = "#result == null")
    fun findByUsername(username: String): UserEntity? = repository.findByUsername(username).getOrNull()

}
