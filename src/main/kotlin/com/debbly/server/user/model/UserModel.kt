package com.debbly.server.user.model

import com.debbly.server.user.UserEntity
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class UserModel(
    val userId: String,
    val externalUserId: String,
    val email: String,
    var username: String? = null,
    var birthdate: LocalDate? = null,
    var avatarUrl: String? = null,
    var rank: Int = 0,
)

fun UserEntity.toModel() = UserModel(
    userId = this.userId,
    externalUserId = this.externalUserId,
    email = this.email,
    username = this.username,
    birthdate = this.birthdate,
    avatarUrl = this.avatarUrl,
    rank = this.rank
)

fun UserModel.toEntity() = UserEntity(
    userId = this.userId,
    externalUserId = this.externalUserId,
    email = this.email,
    username = this.username,
    birthdate = this.birthdate,
    avatarUrl = this.avatarUrl,
    rank = this.rank
)

