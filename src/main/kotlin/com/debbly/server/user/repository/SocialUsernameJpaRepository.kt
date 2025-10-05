package com.debbly.server.user.repository

import com.debbly.server.user.SocialType
import com.debbly.server.user.UserSocialUsernameEntity
import com.debbly.server.user.UserSocialUsernameId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface SocialUsernameJpaRepository : JpaRepository<UserSocialUsernameEntity, UserSocialUsernameId> {
    @Query("SELECT s FROM user_social_usernames s WHERE s.id.userId = :userId")
    fun findAllByUserId(userId: String): List<UserSocialUsernameEntity>

    @Modifying
    @Query("DELETE FROM user_social_usernames s WHERE s.id.userId = :userId")
    fun deleteAllByUserId(userId: String)
}
