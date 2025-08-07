package com.debbly.server.user

import org.springframework.data.jpa.repository.JpaRepository

import java.util.Optional

interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByUsername(username: String): Optional<UserEntity>
    fun findByExternalUserId(externalUserId: String): Optional<UserEntity>
    fun findAllByUserIdIn(ids: List<String>): List<UserEntity>
}
