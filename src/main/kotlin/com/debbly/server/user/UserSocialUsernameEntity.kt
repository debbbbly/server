package com.debbly.server.user

import jakarta.persistence.*
import java.io.Serializable

@Embeddable
data class UserSocialUsernameId(
    val userId: String = "",

    @Enumerated(EnumType.STRING)
    val socialType: SocialType
) : Serializable

@Entity(name = "user_social_usernames")
data class UserSocialUsernameEntity(
    @EmbeddedId
    val id: UserSocialUsernameId,
    var username: String
) {
    fun toModel() = com.debbly.server.user.model.SocialUsernameModel(
        userId = id.userId,
        socialType = id.socialType,
        username = username
    )
}

enum class SocialType {
    INSTAGRAM,
    TWITTER,
    FACEBOOK,
    YOUTUBE,
    DISCORD,
    TIKTOK
}
