package com.debbly.server.moderation

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class ModerationApiService(
    @Value("\${spring.ai.openai.api-key}") private val openaiApiKey: String,
    private val restTemplate: RestTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val moderationUrl = "https://api.openai.com/v1/moderations"

    companion object {
        private const val DEFAULT_MODERATION_EMOJI = "🚫"
        private const val MAX_CHAT_MESSAGE_LENGTH = 500

        private val EMOJI_BY_CATEGORY = mapOf(
            "sexual" to "🍆",
            "sexual/minors" to "🍎",
            "harassment" to "🌶️",
            "harassment/threatening" to "🌶️",
            "hate" to "🍋",
            "hate/threatening" to "🍍",
            "illicit" to "🍄",
            "illicit/violent" to "🌵",
            "self-harm" to "🥀",
            "self-harm/intent" to "🍃",
            "self-harm/instructions" to "🌿",
            "violence" to "🥕",
            "violence/graphic" to "🍅"
        )
    }

    fun validateUsername(username: String): UsernameValidationResult {
        return try {
            val response = callModerationApi(username)
            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val violatedCategories = result.categories.filter { it.value }.keys.joinToString(", ")
                UsernameValidationResult(valid = false, reason = "Username violates platform rules: $violatedCategories")
            } else {
                UsernameValidationResult(valid = true, reason = "")
            }
        } catch (e: Exception) {
            logger.error("Error validating username with moderation API: ${e.message}", e)
            UsernameValidationResult(valid = true, reason = "")
        }
    }

    fun validateAvatar(imageBytes: ByteArray, contentType: String): AvatarValidationResult {
        return try {
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)
            val dataUrl = "data:$contentType;base64,$base64Image"

            val requestBody = mapOf(
                "model" to "omni-moderation-latest",
                "input" to listOf(mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl)))
            )

            val response = callModerationApi(requestBody)
            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val violatedCategories = result.categories.filter { it.value }.keys.joinToString(", ")
                AvatarValidationResult(valid = false, reason = "Avatar violates platform rules: $violatedCategories")
            } else {
                AvatarValidationResult(valid = true, reason = "")
            }
        } catch (e: Exception) {
            logger.error("Error validating avatar with moderation API: ${e.message}", e)
            AvatarValidationResult(valid = true, reason = "")
        }
    }

    fun validateBio(bio: String): BioValidationResult {
        return try {
            val response = callModerationApi(bio)
            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val violatedCategories = result.categories.filter { it.value }.keys.joinToString(", ")
                BioValidationResult(valid = false, reason = "Bio violates platform rules: $violatedCategories")
            } else {
                BioValidationResult(valid = true, reason = "")
            }
        } catch (e: Exception) {
            logger.error("Error validating bio with moderation API: ${e.message}", e)
            BioValidationResult(valid = true, reason = "")
        }
    }

    fun moderateChatMessage(message: String): ChatModerationResult {
        val truncated = if (message.length > MAX_CHAT_MESSAGE_LENGTH) {
            message.take(MAX_CHAT_MESSAGE_LENGTH - 3) + "..."
        } else {
            message
        }

        return try {
            val response = callModerationApi(truncated)
            val result = response?.results?.firstOrNull() ?: return ChatModerationResult(message = truncated, wasModerated = false)

            val flaggedCategories = result.categories
                .filter { it.value }
                .map { (category, _) -> category to (result.categoryScores?.get(category) ?: 0.0) }
                .toMap()

            if (result.flagged && flaggedCategories.any { it.value > 0.65 }) {
                val replacement = flaggedCategories
                    .flatMap { (category, score) ->
                        val emoji = EMOJI_BY_CATEGORY[category] ?: DEFAULT_MODERATION_EMOJI
                        val count = when {
                            score >= 0.95 -> 3
                            score >= 0.85 -> 2
                            else -> 1
                        }
                        List(count) { emoji }
                    }
                    .shuffled()
                    .joinToString("")

                ChatModerationResult(message = replacement, wasModerated = true)
            } else {
                ChatModerationResult(message = truncated, wasModerated = false)
            }
        } catch (e: Exception) {
            logger.error("Error moderating chat message: ${e.message}", e)
            ChatModerationResult(message = truncated, wasModerated = false)
        }
    }

    private fun callModerationApi(input: String): ModerationResponse? {
        return callModerationApi(mapOf("model" to "omni-moderation-latest", "input" to input))
    }

    private fun callModerationApi(requestBody: Map<String, Any>): ModerationResponse? {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(openaiApiKey)
        val request = HttpEntity(requestBody, headers)
        return restTemplate.postForObject(moderationUrl, request, ModerationResponse::class.java)
    }
}

data class UsernameValidationResult(
    val valid: Boolean,
    val reason: String
)

data class AvatarValidationResult(
    val valid: Boolean,
    val reason: String
)

data class BioValidationResult(
    val valid: Boolean,
    val reason: String
)

data class ChatModerationResult(
    val message: String,
    val wasModerated: Boolean
)

data class ModerationResponse(
    val id: String,
    val model: String,
    val results: List<ModerationResult>
)

data class ModerationResult(
    val flagged: Boolean,
    val categories: Map<String, Boolean>,
    @JsonProperty("category_scores")
    val categoryScores: Map<String, Double>? = null
)
