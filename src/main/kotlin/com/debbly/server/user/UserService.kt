package com.debbly.server.user

import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
class UserService(private val repository: UserRepository) {

    fun create(user: UserEntity): UserEntity = repository.save(user)

    fun getById(userId: String): UserEntity = repository.findById(userId)
        .orElseThrow { NoSuchElementException("User not found") }

    fun findById(userId: String): UserEntity? = repository.findById(userId).getOrNull()


    fun complete(
        userId: String,
        username: String,
        birthdate: LocalDate
    ) {
        if (repository.findByUsername(username).isPresent) {
            throw IllegalArgumentException("Username already taken")
        }

        val user = repository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }

        user.username = username
        user.birthdate = birthdate

        repository.save(user)
    }

    fun findByUsername(username: String): UserEntity? = repository.findByUsername(username).getOrNull()

}
