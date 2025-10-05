package com.debbly.server.user.repository

import com.debbly.server.user.SocialType
import com.debbly.server.user.UserSocialUsernameId
import com.debbly.server.user.model.SocialUsernameModel
import com.debbly.server.user.model.toEntity
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
class SocialUsernameCachedRepository(
    private val socialUsernameJpaRepository: SocialUsernameJpaRepository
) {

    @Cacheable(value = ["socialUsernames"], key = "#userId", unless = "#result == null || #result.isEmpty()")
    fun findAllByUserId(userId: String): List<SocialUsernameModel> =
        socialUsernameJpaRepository.findAllByUserId(userId).map { it.toModel() }

    fun findByUserIdAndSocialType(userId: String, socialType: SocialType): SocialUsernameModel? =
        socialUsernameJpaRepository.findById(UserSocialUsernameId(userId, socialType)).getOrNull()?.toModel()

    @CacheEvict(value = ["socialUsernames"], key = "#result.userId")
    fun save(socialUsername: SocialUsernameModel): SocialUsernameModel =
        socialUsernameJpaRepository.save(socialUsername.toEntity()).toModel()

    @Transactional
    @CacheEvict(value = ["socialUsernames"], key = "#userId")
    fun saveAll(userId: String, socialUsernames: List<SocialUsernameModel>): List<SocialUsernameModel> {
        // Delete existing entries
        socialUsernameJpaRepository.deleteAllByUserId(userId)

        // Save new entries
        return socialUsernames.map {
            socialUsernameJpaRepository.save(it.toEntity()).toModel()
        }
    }

    @CacheEvict(value = ["socialUsernames"], key = "#userId")
    fun deleteAllByUserId(userId: String) {
        socialUsernameJpaRepository.deleteAllByUserId(userId)
    }
}
