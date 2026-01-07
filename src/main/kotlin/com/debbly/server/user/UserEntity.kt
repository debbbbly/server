package com.debbly.server.user

import com.debbly.server.user.model.UserModel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.Instant
import java.time.LocalDate

@Entity(name = "users")
data class UserEntity(
    @Id
    val userId: String,
    @Column(unique = true)
    val externalUserId: String,
    var email: String,
    var username: String,
    var usernameNormalized: String,
    var birthdate: LocalDate? = null,
    var avatarUrl: String? = null,
    var rank: Int = 0,
    var deleted: Boolean = false,
    @Column(length = 1024)
    var bio: String? = null,
    @Column(name = "created_at")
    var createdAt: Instant,
    @Column(name = "last_login")
    var lastLogin: Instant? = null,
    @Column(name = "last_seen")
    var lastSeen: Instant? = null,
) {
    fun toModel() = UserModel(
        userId = userId,
        externalUserId = externalUserId,
        email = email,
        username = username,
        usernameNormalized = usernameNormalized,
        birthdate = birthdate,
        avatarUrl = avatarUrl,
        rank = rank,
        deleted = deleted,
        bio = bio,
        createdAt = createdAt,
        lastLogin = lastLogin,
        lastSeen = lastSeen,
    )
}

