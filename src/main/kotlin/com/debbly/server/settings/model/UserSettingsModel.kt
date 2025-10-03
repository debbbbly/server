package com.debbly.server.settings.model

import com.debbly.server.settings.UserSettingsEntity
import com.debbly.server.settings.UserSettingsName
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class UserSettingsModel(
    val id: String?,
    val userId: String,
    val name: UserSettingsName,
    val value: String
)

fun UserSettingsEntity.toModel() = UserSettingsModel(
    id = this.id,
    userId = this.userId,
    name = this.name,
    value = this.value
)

fun UserSettingsModel.toEntity() = UserSettingsEntity(
    id = this.id,
    userId = this.userId,
    name = this.name,
    value = this.value
)
