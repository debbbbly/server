package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAIService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val openAIService: OpenAIService
) {

    fun createUser(externalUserId: String, email: String): UserModel {
        val existingUser = userCachedRepository.findByExternalUserId(externalUserId)
        if (existingUser != null) {
            return existingUser
        }

        val generatedUsernames = openAIService.generateUsernames(email)
        val username = generatedUsernames.firstOrNull() ?: "User${idService.getId().take(6)}"
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
}