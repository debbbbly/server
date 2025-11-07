package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAIService
import com.debbly.server.auth.service.AuthService
import com.debbly.server.storage.S3Service
import com.debbly.server.user.UserValidator.isValidUsername
import com.debbly.server.user.model.SocialUsernameModel
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.SocialUsernameCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import kotlin.random.Random

@Service
class UserService(
    private val userCachedRepository: UserCachedRepository,
    private val socialUsernameCachedRepository: SocialUsernameCachedRepository,
    private val idService: IdService,
    private val openAIService: OpenAIService,
    private val s3Service: S3Service,
    private val cacheManager: CacheManager,
    private val authService: AuthService
) {

    fun createUser(externalUserId: String, email: String): UserModel {
        val existingUser = userCachedRepository.findByExternalUserId(externalUserId)
        if (existingUser != null) {
            return existingUser
        }

        val generatedUsernames = openAIService.generateUsernames(email)
        val username = takeAvailable(generatedUsernames)
        val avatarUrl = "https://api.dicebear.com/9.x/initials/svg?seed=${username}"

        val newUser = UserModel(
            userId = idService.getId(),
            externalUserId = externalUserId,
            email = email,
            username = username,
            avatarUrl = avatarUrl
        )

        return userCachedRepository.save(newUser)
    }

    private fun takeAvailable(generatedUsernames: List<String>): String {
        for (username in generatedUsernames) {
            if (userCachedRepository.findByUsername(username) == null) {
                return username
            }
        }

        return (generatedUsernames.firstOrNull() ?: "User") + Random.nextInt(100000, 999999)
    }

    fun updateUsername(user: UserModel, newUsername: String, accessToken: String? = null): UpdateUsernameResult {
        // Validate format
        if (!isValidUsername(newUsername)) {
            return UpdateUsernameResult(
                success = false,
                message = "Invalid username format. Must be 5-30 characters, alphanumeric and underscores only"
            )
        }

        // Check if username is already taken
        val existingUser = userCachedRepository.findByUsername(newUsername.trim())
        if (existingUser != null && existingUser.userId != user.userId) {
            return UpdateUsernameResult(
                success = false,
                message = "Username is already taken"
            )
        }

        // Validate with AI
        val aiValidation = openAIService.validateUsername(newUsername.trim())
        if (!aiValidation.valid) {
            return UpdateUsernameResult(
                success = false,
                message = "Username violates platform rules: ${aiValidation.reason}"
            )
        }

        // Evict old username from cache
        user.username?.let { oldUsername ->
            cacheManager.getCache("usersByUsername")?.evict(oldUsername)
        }

        // Update username
        user.username = newUsername.trim()
        userCachedRepository.save(user)

        // Sync username with Supabase metadata
        if (accessToken != null) {
            val metadata = mapOf("username" to newUsername.trim())
            authService.updateUserMetadata(accessToken, metadata)
        }

        return UpdateUsernameResult(
            success = true,
            message = "Username updated successfully"
        )
    }

    fun updateAvatar(user: UserModel, file: MultipartFile): UpdateAvatarResult {
        // Validate content type
        val contentType = file.contentType
        if (contentType == null || !contentType.startsWith("image/")) {
            return UpdateAvatarResult(
                success = false,
                message = "File must be an image",
                avatarUrl = null
            )
        }

        // Validate file size
        val maxSizeBytes = 5 * 1024 * 1024
        if (file.size > maxSizeBytes) {
            return UpdateAvatarResult(
                success = false,
                message = "File size must be less than 5MB",
                avatarUrl = null
            )
        }

        val aiValidation = openAIService.validateAvatar(file.bytes, contentType)
        if (!aiValidation.valid) {
            return UpdateAvatarResult(
                success = false,
                message =  aiValidation.reason,
                avatarUrl = null
            )
        }

        // Delete old avatar
        user.avatarUrl?.let { oldUrl ->
            if (oldUrl.contains(s3Service::class.simpleName ?: "")) {
                try {
                    s3Service.deleteAvatar(oldUrl)
                } catch (e: Exception) {
                    // Log error but continue with upload
                    println("Failed to delete old avatar: ${e.message}")
                }
            }
        }

        // Upload new avatar
        val avatarUrl = s3Service.uploadAvatar(file, user.userId)
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
        val aiValidation = openAIService.validateBio(newBio.trim())
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