package com.debbly.server.settings

import jakarta.persistence.*

@Entity(name = "user_settings")
@Table(
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["userId", "name"])
    ]
)
data class UserSettingsEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    val userId: String,

    @Enumerated(EnumType.STRING)
    val name: UserSettingsName,

    val value: String
)
