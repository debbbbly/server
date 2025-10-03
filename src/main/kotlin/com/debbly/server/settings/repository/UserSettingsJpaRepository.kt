package com.debbly.server.settings.repository

import com.debbly.server.settings.UserSettingsEntity
import com.debbly.server.settings.UserSettingsName
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserSettingsJpaRepository : JpaRepository<UserSettingsEntity, String> {
    fun findByUserIdAndName(userId: String, name: UserSettingsName): Optional<UserSettingsEntity>
    fun findAllByUserId(userId: String): List<UserSettingsEntity>
}
