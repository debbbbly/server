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
    val email: String,
    @Column(unique = true)
    var username: String? = null,
    var birthdate: LocalDate? = null,
    var avatarUrl: String? = null,
)

