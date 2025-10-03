package com.debbly.server.settings.repository

import com.debbly.server.settings.UserSettingsName
import com.debbly.server.settings.model.UserSettingsModel
import com.debbly.server.settings.model.toEntity
import com.debbly.server.settings.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class UserSettingsCachedRepository(
    private val userSettingsJpaRepository: UserSettingsJpaRepository
) {

    @Cacheable(value = ["userSettings"], key = "#userId + ':' + #name", unless = "#result == null")
    fun findByUserIdAndName(userId: String, name: UserSettingsName): UserSettingsModel? {
        return userSettingsJpaRepository.findByUserIdAndName(userId, name).getOrNull()?.toModel()
    }

    @Cacheable(value = ["userSettingsByUser"], key = "#userId")
    fun findAllByUserId(userId: String): List<UserSettingsModel> {
        return userSettingsJpaRepository.findAllByUserId(userId).map { it.toModel() }
    }

    @CacheEvict(value = ["userSettings", "userSettingsByUser"], key = "#userSetting.userId + ':' + #userSetting.name")
    fun save(userSetting: UserSettingsModel): UserSettingsModel {
        return userSettingsJpaRepository.save(userSetting.toEntity()).toModel()
    }

    @CacheEvict(value = ["userSettings", "userSettingsByUser"], allEntries = true)
    fun delete(userSetting: UserSettingsModel) {
        userSetting.id?.let { userSettingsJpaRepository.deleteById(it) }
    }
}
