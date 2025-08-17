package com.debbly.server.user.repository

import com.debbly.server.user.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

import java.util.Optional

interface UserJpaRepository : JpaRepository<UserEntity, String> {
    fun findByUsername(username: String): Optional<UserEntity>
    fun findByExternalUserId(externalUserId: String): Optional<UserEntity>
    fun findAllByUserIdIn(ids: List<String>): List<UserEntity>
}
