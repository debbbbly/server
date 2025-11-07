package com.debbly.server.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.LocalDate

@Entity(name = "users")
data class UserEntity(
    @Id
    val userId: String,
    @Column(unique = true)
    val externalUserId: String,
    var email: String,
    @Column(unique = true)
    var username: String,
    var birthdate: LocalDate? = null,
    var avatarUrl: String? = null,
    var rank: Int = 0,
    var deleted: Boolean = false,
    @Column(length = 1024)
    var bio: String? = null,
) {
    fun toModel() = com.debbly.server.user.model.UserModel(
        userId = userId,
        externalUserId = externalUserId,
        email = email,
        username = username,
        birthdate = birthdate,
        avatarUrl = avatarUrl,
        rank = rank,
        deleted = deleted,
        bio = bio,
    )
}

