package com.debbly.server.user.model

import com.debbly.server.user.UserEntity
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.time.LocalDate

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class UserModel(
    val userId: String,
    val externalUserId: String,
    var email: String,
    var username: String,
    var usernameNormalized: String,
    var birthdate: LocalDate? = null,
    var avatarUrl: String? = null,
    var rank: Int = 0,
    var deleted: Boolean = false,
    var banned: Boolean = false,
    var bio: String? = null,
    var createdAt: Instant,
    var lastLogin: Instant? = null,
    var lastSeen: Instant? = null,
)

fun UserEntity.toModel() = UserModel(
    userId = this.userId,
    externalUserId = this.externalUserId,
    email = this.email,
    username = this.username,
    usernameNormalized = this.usernameNormalized,
    birthdate = this.birthdate,
    avatarUrl = this.avatarUrl,
    rank = this.rank,
    deleted = this.deleted,
    banned = this.banned,
    bio = this.bio,
    createdAt = this.createdAt,
    lastLogin = this.lastLogin,
    lastSeen = this.lastSeen
)

fun UserModel.toEntity() = UserEntity(
    userId = this.userId,
    externalUserId = this.externalUserId,
    email = this.email,
    username = this.username,
    usernameNormalized = this.usernameNormalized,
    birthdate = this.birthdate,
    avatarUrl = this.avatarUrl,
    rank = this.rank,
    deleted = this.deleted,
    banned = this.banned,
    bio = this.bio,
    createdAt = this.createdAt,
    lastLogin = this.lastLogin,
    lastSeen = this.lastSeen
)

