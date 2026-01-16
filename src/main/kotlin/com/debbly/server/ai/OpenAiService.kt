package com.debbly.server.ai

import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.TopicStance
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class OpenAiService(
    private val chatClient: ChatClient,
    private val categoryRepository: CategoryCachedRepository,
    @Value("\${spring.ai.openai.api-key}") private val openaiApiKey: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val restTemplate = RestTemplate()
    private val moderationUrl = "https://api.openai.com/v1/moderations"
    private val embeddingUrl = "https://api.openai.com/v1/embeddings"

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

    fun moderateClaim(title: String): ClaimModerationResult {
        val prompt = """
            You are a content moderator and classifier for an online debating platform 
            (like Twitch, but for debates). Users submit short claims that serve as the starting 
            point for debates. Your task is to evaluate each claim against the platform rules, 
            normalize it, and enrich it with categories, topic (core proposition) and stance to the topic.

            Instructions:
            For the given user claim: "$title"

            1. Check if the claim follows ALL platform rules.
            2. If claim is valid:
                - Assign exactly ONE main category.
                - Extract a single neutral topic (if you cannot extract a topic, the claim is invalid).
                - Determine the stance of the claim toward the extracted topic.
            3. If claim is invalid:
                - List each violated rule explicitly.
                - Reasoning must be concise (1–2 sentences).
                - Set "categoryId", "topic" and "stance" to null.
            4. Respond ONLY with a single valid JSON object. No text outside JSON.

            Platform Rules:
            - The claim must be debatable: reasonable people could disagree about it.
            - The claim must be clear and specific enough to spark discussion.
              (If broad but still conveys a clear controversial stance, accept it.)
            - The claim MUST have an extractable neutral topic. If you cannot identify a clear topic
              that this claim is about, mark the claim as invalid.
            - The claim must not contain personal attacks against private individuals.
              (Criticism of public figures' actions, policies, or ideas is acceptable.)
            - The claim must not contain hate speech.
              (If it targets an immutable identity with exclusion or inferiority → invalid.
               If it critiques behavior, policy, status, or law - valid, even if offensive.)
            - The claim must not actively promote committing illegal acts.
              (Debates about changing laws/policies are allowed,
               except if the change would legalize violence, exploitation, or denial of 
               fundamental human rights.)
            - The claim must not mention sexual organs, sexual activity, or explicit anatomy
              UNLESS the claim is clearly framed as a serious policy, educational, or medical debate.
              By default, any casual reference to sex or anatomy should be treated as invalid,
              even if it could be technically debatable.
            - The claim must not contain spam, promotional content, or advertising.
            - The claim must not be nonsense, gibberish, or irrelevant platform meta-comments.
            - The claim must not be frivolous, silly, or obviously low-value.
              (Claims should be framed in a way that could lead to a meaningful debate.)

            Claim Normalization Requirements:
            - If the claim is valid, provide a normalized version:
            - Correct spelling/grammar, but do NOT add a period at the end (claims are standalone statements, not sentences in prose)
            - Remove emojis, special symbols, random punctuation
            - Remove ALL CAPS shouting (convert to sentence case or title case if appropriate)
            - Normalize slang and contractions into standard English where possible.
            - Clarify vague claims into specific, debatable statements while keeping intent
            
            Claim Categories (categoryId / title):
            - politics / Politics
            - technology / Technology & Science
            - society / Society, Identity & Culture
            - economy / Economy & Environment
            - entertainment / Sports, Entertainment & Lifestyle
            (Default: society if unsure)
            
            Claim Topic (also called the core proposition) Requirements:
            
            - The claim topic must:
                - Be phrased as a neutral, stance-free statement
                - Represent what people would recognize as the main debate topic
                  if they saw this claim in a comment section or feed
                - Be reusable across opposing claims
                - Be broad enough to group future related claims added separately

            - Topic selection rules:
                - If the claim references a named real-world event, scandal, incident, or widely reported issue, extract the topic as THAT controversy itself, not the specific argument angle used in the claim.
                - Do NOT frame the topic around media framing, consequences, legality, or blame if those are reactions to the same underlying controversy.
                - Prefer controversy-centered or action-centered topics over opinion- or angle-centered topics.
                - Only use angle-specific topics if NO clear underlying event, action, or controversy can be identified.

            - The topic MUST NOT:
                - Express approval or disapproval
                - Be limited to the author’s specific criticism or defense
                
            Claim Topic Examples:

            1. Claims :
                - “The ICE agent's use of deadly force was unjustified”
                - “The ICE officer was justified in using lethal force in Minneapolis”
                - “The fatal ICE shooting was an abuse of power”
            Topic :
                - “The justification of ICE officers’ use of lethal force”
            
            2. Claims:
                - “Seizing oil tankers will escalate geopolitical tensions”
                - “The U.S. was justified in seizing an oil tanker”
                - “The seizure of a Russian-flagged oil tanker violated international law”
            Topic:
                - “The legality and geopolitical consequences of oil tanker seizures”
                
            3. Claims:
                - “The Minnesota welfare fraud story is exaggerated to attack immigrants”
                - “State leaders completely dropped the ball on welfare fraud in Minnesota”
                - “Minnesota’s welfare fraud proves the system is being abused”
            Topic:
                - “The Minnesota welfare fraud scandal”
            
            Stance Requirements:
            - Allowed values:
              - "FOR" → supports or affirms the topic
              - "AGAINST" → opposes or rejects the topic
              - "NEUTRAL" → descriptive, unclear, or balanced
            - The stance must be inferred from the claim’s intent, not just wording.

            Output Format:
            {
              "valid": true/false,
              "normalized": "cleaned-up version (empty string if invalid)",
              "violations": ["string", "string"],
              "reasoning": "short explanation (1–2 sentences)",
              "categoryId": "string (required when valid=true, null when valid=false)",
              "topic": "neutral topic extracted from the claim (required when valid=true, null when valid=false)",
              "stance": "FOR|AGAINST|NEUTRAL (required when valid=true, null when valid=false)"
            }
            
            Examples:
                Claim: "The benefits of artificial intelligence outweigh its risks to society"
                Response: 
                {
                  "valid": true,
                  "normalized": "The benefits of AI outweigh its risks to society",
                  "violations": [],
                  "reasoning": "The claim is clear, specific, and debatable without violating any platform rules",
                  "categoryId": "technology",
                  "topic": "The overall impact of AI on society",
                  "stance": "FOR"
                }
                
                Claim: "Governments should prioritize climate change mitigation over economic growth"
                Response: 
                {
                  "valid": true,
                  "normalized": "Governments should prioritize climate change mitigation over economic growth",
                  "violations": [],
                  "reasoning": "The claim is specific and debatable, addressing a significant policy issue.",
                  "categoryId": "economy",
                  "topic": "The prioritization of climate change mitigation versus economic growth in government policy",
                  "stance": "FOR"
                }
                
                Claim: "Anyone who disagrees with climate science is an idiot"
                Response: 
                {
                  "valid": false,
                  "normalized": null,
                  "violations": ["Personal attacks against individuals or groups"],
                  "reasoning": "The claim contains an insulting personal attack rather than a debatable position",
                  "categoryId": null,
                  "topic": null,
                  "stance": null
                }
            """.trimIndent()

        return try {
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: ""

            objectMapper.readValue<ClaimModerationResult>(response)

        } catch (e: Exception) {
            logger.error("Error validating claim: ${e.message}", e)
            throw e
        }
    }

    fun validateUsername(username: String): UsernameValidationResult {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            val requestBody = mapOf(
                "model" to "omni-moderation-latest",
                "input" to username
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(
                moderationUrl,
                request,
                ModerationResponse::class.java
            )

            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val violatedCategories = result.categories
                    .filter { it.value }
                    .keys
                    .joinToString(", ")

                UsernameValidationResult(
                    valid = false,
                    reason = "Username violates platform rules: $violatedCategories"
                )
            } else {
                UsernameValidationResult(valid = true, reason = "")
            }
        } catch (e: Exception) {
            logger.error("Error validating username with moderation API: ${e.message}", e)
            // Fail open - allow the username if moderation check fails
            UsernameValidationResult(valid = true, reason = "")
        }
    }

    fun validateAvatar(imageBytes: ByteArray, contentType: String): AvatarValidationResult {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            // Convert image to base64
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)
            val dataUrl = "data:$contentType;base64,$base64Image"

            val requestBody = mapOf(
                "model" to "omni-moderation-latest",
                "input" to listOf(
                    mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to dataUrl)
                    )
                )
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(
                moderationUrl,
                request,
                ModerationResponse::class.java
            )

            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val violatedCategories = result.categories
                    .filter { it.value }
                    .keys
                    .joinToString(", ")

                AvatarValidationResult(
                    valid = false,
                    reason = "Avatar violates platform rules: $violatedCategories"
                )
            } else {
                AvatarValidationResult(valid = true, reason = "")
            }
        } catch (e: Exception) {
            logger.error("Error validating avatar with moderation API: ${e.message}", e)
            // Fail open - allow the avatar if moderation check fails
            AvatarValidationResult(valid = true, reason = "")
        }
    }

    fun validateBio(bio: String): BioValidationResult {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            val requestBody = mapOf(
                "model" to "omni-moderation-latest",
                "input" to bio
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(
                moderationUrl,
                request,
                ModerationResponse::class.java
            )

            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val violatedCategories = result.categories
                    .filter { it.value }
                    .keys
                    .joinToString(", ")

                BioValidationResult(
                    valid = false,
                    reason = "Bio violates platform rules: $violatedCategories"
                )
            } else {
                BioValidationResult(valid = true, reason = "")
            }
        } catch (e: Exception) {
            logger.error("Error validating bio with moderation API: ${e.message}", e)
            // Fail open - allow the bio if moderation check fails
            BioValidationResult(valid = true, reason = "")
        }
    }

    fun moderateChatMessage(message: String): ChatModerationResult {
        // Truncate message if too long
        val processedMessage = if (message.length > MAX_CHAT_MESSAGE_LENGTH) {
            message.take(MAX_CHAT_MESSAGE_LENGTH - 3) + "..."
        } else {
            message
        }

        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            val requestBody = mapOf(
                "model" to "omni-moderation-latest",
                "input" to processedMessage
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(
                moderationUrl,
                request,
                ModerationResponse::class.java
            )

            val result = response?.results?.firstOrNull() ?: return ChatModerationResult(
                message = processedMessage,
                wasModerated = false
            )

            val flaggedCategories = result.categories
                .filter { it.value }
                .map { (category, _) -> category to (result.categoryScores?.get(category) ?: 0.0) }
                .toMap()

            if (result.flagged && flaggedCategories.any { it.value > 0.65 }) {
                val replacement = flaggedCategories // Only flagged categories
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

                ChatModerationResult(
                    message = replacement,
                    wasModerated = true
                )
            } else {
                ChatModerationResult(
                    message = processedMessage,
                    wasModerated = false
                )
            }
        } catch (e: Exception) {
            logger.error("Error moderating chat message: ${e.message}", e)
            // Fail open - return processed (truncated) message if moderation check fails
            ChatModerationResult(
                message = processedMessage,
                wasModerated = false
            )
        }
    }

    fun generateEmbedding(text: String): List<Double>? {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            val requestBody = mapOf(
                "model" to "text-embedding-3-small",
                "input" to text
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(
                embeddingUrl,
                request,
                EmbeddingResponse::class.java
            )

            response?.data?.firstOrNull()?.embedding
        } catch (e: Exception) {
            logger.error("Error generating embedding: ${e.message}", e)
            null
        }
    }
}

// OpenAI Embeddings API response structures
data class EmbeddingResponse(
    val `object`: String,
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage
)

data class EmbeddingData(
    val `object`: String,
    val embedding: List<Double>,
    val index: Int
)

data class EmbeddingUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @JsonProperty("total_tokens")
    val totalTokens: Int
)

data class ClaimModerationResult(
    val valid: Boolean,
    val normalized: String? = null,
    val violations: List<String> = emptyList(),
    val reasoning: String? = null,
    val categoryId: String? = null,
    val topic: String? = null,
    val stance: TopicStance? = null,
)

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

data class ChatModerationResult(
    val message: String,
    val wasModerated: Boolean
)