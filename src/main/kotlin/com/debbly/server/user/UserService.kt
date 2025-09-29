package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAIService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

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
}