package com.debbly.server.user.repository

import com.debbly.server.user.UserEntity
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

import java.util.Optional

interface UserJpaRepository : JpaRepository<UserEntity, String> {
    fun findByUsername(username: String): Optional<UserEntity>
    fun findByUsernameNormalized(usernameNormalized: String): Optional<UserEntity>
    fun findByExternalUserId(externalUserId: String): Optional<UserEntity>
    fun findAllByUserIdIn(ids: List<String>): List<UserEntity>

    @Query("SELECT u FROM users u ORDER BY u.rank DESC")
    fun findTop100ByRankDesc(pageable: PageRequest): List<UserEntity>
}
