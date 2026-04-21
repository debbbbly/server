package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.auth.service.AuthService
import com.debbly.server.moderation.ModerationApiService
import com.debbly.server.storage.S3Service
import com.debbly.server.user.model.SocialUsernameModel
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.SocialUsernameCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant.now

@Service
class UserService(
    private val userCachedRepository: UserCachedRepository,
    private val socialUsernameCachedRepository: SocialUsernameCachedRepository,
    private val idService: IdService,
    private val moderationApiService: ModerationApiService,
    private val s3Service: S3Service,
    private val cacheManager: CacheManager,
    private val authService: AuthService,
    private val usernameService: UsernameService,
    private val clock: Clock
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createUser(externalUserId: String, email: String): UserModel {
        val existingUser = userCachedRepository.findByExternalUserId(externalUserId)
        if (existingUser != null) {
            return existingUser
        }

        val username = usernameService.generateUsername()
        val avatarUrl = "https://api.dicebear.com/9.x/initials/svg?seed=${username}"

        val newUser = UserModel(
            userId = idService.getId(),
            externalUserId = externalUserId,
            email = email,
            username = username,
            usernameNormalized = username.lowercase(),
            avatarUrl = avatarUrl,
            createdAt = now(clock)
        )

        return userCachedRepository.save(newUser)
    }

    fun updateUsername(user: UserModel, newUsername: String, accessToken: String? = null): UpdateUsernameResult {
        val result = usernameService.validateUsername(newUsername, user.userId)
        if (!result.valid) {
            return UpdateUsernameResult(
                success = false,
                message = result.reason
            )
        }

        val trimmedUsername = newUsername.trim()

        cacheManager.getCache("usersByUsername")?.evict(user.usernameNormalized)

        user.username = trimmedUsername
        user.usernameNormalized = trimmedUsername.lowercase()
        userCachedRepository.save(user)

        if (accessToken != null) {
            val metadata = mapOf("username" to trimmedUsername)
            authService.updateUserMetadata(accessToken, metadata)
        }

        return UpdateUsernameResult(
            success = true,
            message = "Username updated successfully"
        )
    }

    fun updateAvatar(user: UserModel, avatarKey: String): UpdateAvatarResult {
        if (!s3Service.isAvatarKeyOwnedByUser(user.userId, avatarKey)) {
            return UpdateAvatarResult(
                success = false,
                message = "Invalid avatar key",
                avatarUrl = null
            )
        }

        // Delete old avatar if it is in our users bucket
        user.avatarUrl?.let { oldUrl ->
            if (s3Service.isUsersPublicUrl(oldUrl)) {
                try {
                    s3Service.deleteAvatar(oldUrl)
                } catch (e: Exception) {
                    logger.warn("Failed to delete old avatar: ${e.message}")
                }
            }
        }

        val avatarUrl = s3Service.buildUsersPublicUrl(avatarKey)
        user.avatarUrl = avatarUrl
        userCachedRepository.save(user)

        return UpdateAvatarResult(
            success = true,
            message = "Avatar updated successfully",
            avatarUrl = avatarUrl
        )
    }

    fun updateBio(user: UserModel, newBio: String): UpdateBioResult {
        // Validate bio length
        if (newBio.length > 1024) {
            return UpdateBioResult(
                success = false,
                message = "Bio must be 1024 characters or less"
            )
        }

        // Validate with AI
        val aiValidation = moderationApiService.validateBio(newBio.trim())
        if (!aiValidation.valid) {
            return UpdateBioResult(
                success = false,
                message = "Bio violates platform rules: ${aiValidation.reason}"
            )
        }

        // Update bio
        user.bio = newBio.trim()
        userCachedRepository.save(user)

        return UpdateBioResult(
            success = true,
            message = "Bio updated successfully"
        )
    }

    fun updateSocialUsernames(
        userId: String,
        socialUsernames: Map<SocialType, String>
    ): UpdateSocialUsernamesResult {
        // Validate all usernames
        for ((socialType, username) in socialUsernames) {
            if (username.isBlank()) {
                return UpdateSocialUsernamesResult(
                    success = false,
                    message = "Username for ${socialType.name} cannot be blank"
                )
            }
            if (username.length > 255) {
                return UpdateSocialUsernamesResult(
                    success = false,
                    message = "Username for ${socialType.name} must be 255 characters or less"
                )
            }
        }

        // Convert map to list of models
        val models = socialUsernames.map { (socialType, username) ->
            SocialUsernameModel(
                userId = userId,
                socialType = socialType,
                username = username.trim()
            )
        }

        // Save all
        socialUsernameCachedRepository.saveAll(userId, models)

        return UpdateSocialUsernamesResult(
            success = true,
            message = "Social usernames updated successfully"
        )
    }
}

data class UpdateUsernameResult(
    val success: Boolean,
    val message: String
)

data class UpdateAvatarResult(
    val success: Boolean,
    val message: String,
    val avatarUrl: String?
)

data class UpdateBioResult(
    val success: Boolean,
    val message: String
)

data class UpdateSocialUsernamesResult(
    val success: Boolean,
    val message: String
)
