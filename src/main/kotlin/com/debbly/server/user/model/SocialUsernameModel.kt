package com.debbly.server.user.model

import com.debbly.server.user.SocialType
import com.debbly.server.user.UserSocialUsernameEntity
import com.debbly.server.user.UserSocialUsernameId

data class SocialUsernameModel(
    val userId: String,
    val socialType: SocialType,
    var username: String
)

fun UserSocialUsernameEntity.toModel() = SocialUsernameModel(
    userId = this.id.userId,
    socialType = this.id.socialType,
    username = this.username
)

fun SocialUsernameModel.toEntity() = UserSocialUsernameEntity(
    id = UserSocialUsernameId(
        userId = this.userId,
        socialType = this.socialType
    ),
    username = this.username
)
