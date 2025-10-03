package com.debbly.server.settings

import jakarta.persistence.*

@Entity(name = "settings")
data class SettingsEntity(
    @Id
    @Enumerated(EnumType.STRING)
    val name: SettingsName,

    val value: String
)
