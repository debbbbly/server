package com.debbly.server.settings.repository

import com.debbly.server.settings.SettingsEntity
import com.debbly.server.settings.SettingsName
import org.springframework.data.jpa.repository.JpaRepository

interface SettingsJpaRepository : JpaRepository<SettingsEntity, SettingsName>
