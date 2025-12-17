package com.debbly.server.ai

import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.user.UserValidator.invalidCharsRegex
import com.debbly.server.user.UserValidator.usernameRegex
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
import java.util.Base64

@Service
class OpenAIService(
    private val chatClient: ChatClient,
    private val categoryRepository: CategoryCachedRepository,
    @Value("\${spring.ai.openai.api-key}") private val openaiApiKey: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val restTemplate = RestTemplate()
    private val moderationUrl = "https://api.openai.com/v1/moderations"

    companion object {
        private val MODERATION_REPLACEMENT_MESSAGES = listOf(
            "I was about to say something… but moderation saved us all",
            "My brain started a sentence and immediately shut it down",
            "My thoughts did not pass internal review",
            "I had a point. It ran away",
            "I almost typed something",
            "Let's pretend this message was very smart",
            "I forgot what I was trying to say",
            "My thoughts are not PG-13 right now",
            "This message was canceled",
            "I decided silence is better",
            "This sounded better in my head",
            "I started typing and lost confidence",
            "I will respectfully not finish my thought",
            "My brain said \"nope\"",
            "My message failed the vibe check",
            "I had a sentence, then reconsidered my life choices",
            "I chose not to send the original message",
            "My thoughts took a wrong turn",
            "I almost said something",
            "I stopped myself just in time",
            "I'll pretend this message made sense",
            "I started typing and immediately regretted it",
            "This thought did not age well",
            "I'm not sure what I was trying to achieve here",
            "I decided not to continue this sentence",
            "That idea sounded better five seconds ago",
            "I had words. They're gone now",
            "I'm going to leave this unfinished",
            "My brain rebooted mid-message",
            "This message is a placeholder for a better one",
            "I was about to say something unnecessary",
            "I forgot my point halfway through",
            "I'll save my thought for never",
            "I stopped typing for everyone's benefit",
            "I realized this wasn't worth finishing",
            "I had a thought. It expired",
            "Love"
        )
    }

    fun validateClaim(title: String): ClaimValidationResult {
        val prompt = """
            You are a content moderator and classifier for an online debating platform 
            (like Twitch, but for debates). Users submit short claims that serve as the starting 
            point for debates. Your task is to evaluate each claim against the platform rules, 
            normalize it, and enrich it with categories and tags.

            Platform Rules:
            - The claim must be debatable: reasonable people could disagree about it.
            - The claim must be clear and specific enough to spark discussion.
              (If broad but still conveys a clear controversial stance, accept it.)
            - The claim must not contain personal attacks against private individuals.
              (Criticism of public figures’ actions, policies, or ideas is acceptable.)
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
            
            Platform Claim Categories (categoryId / title):
            - politics / Politics
            - technology-innovation / Technology & Innovation
            - social-issues-culture / Social Issues & Culture
            - economy-environment / Economy & Environment
            - sports-entertainment-lifestyle / Sports, Entertainment & Lifestyle
            (Default: social-issues-culture if unsure)
            
            Tag requirements:
            - Assign 1–2 subject tags (entities, groups, or concrete topics: AI, Twich, Climate Change, US, Vaccines, etc.)
            - Assign 1–2 domain tags (broader thematic areas: Technology, Law & Justice, Economics, Health, Education, etc.)
            - Optionally assign 0–1 sensitivity tag if applicable (Conspiracy, Drugs, Sex, Violence, Religion, Controversial, etc.)
            - Tags must be distinct from one another
            - Tags must be in singular form unless the entity is universally known in plural (e.g. Human Rights)
            - Always apply formatting rules:
                - Use the standard abbreviation instead of the full phrase for widely recognized terms:
                    -- Artificial Intelligence → AI
                    -- United States → US
                    -- Information Technology → IT
                - Always prefer common abbreviation over a long form
                - Do not create uncommon or ambiguous abbreviations
            
            Instructions:
            For the given user claim: "$title"

            1. Check if the claim follows ALL platform rules.
            2. If invalid, list each violated rule explicitly.
            3. Assign exactly ONE main category.
            4. Assign 2–5 tags (subject, domain, and sensitivity) applying Tag formatting rules.
            5. Respond ONLY with a single valid JSON object. No text outside JSON.
            6. Reasoning must be concise (1–2 sentences).

            Output Format:
            {
              "valid": true/false,
              "normalized": "cleaned-up version (empty string if invalid)",
              "violations": ["string", "string"],
              "reasoning": "short explanation (1–2 sentences)",
              "categoryId": "string",
              "tags": ["string", "string", "string", "string"]
            }
            
            Examples:
                Claim: "The benefits of artificial intelligence outweigh its risks to society"
                Response: 
                {
                  "valid": true,
                  "normalized": "The benefits of AI outweigh its risks to society.",
                  "violations": [],
                  "reasoning": "The claim is clear, specific, and debatable without violating any platform rules",
                  "categoryId": "technology-innovation",
                  "tags": ["AI", "Technology", "Society"]
                }
                
                Claim: "Governments should prioritize climate change mitigation over economic growth"
                Response: 
                {
                  "valid": true,
                  "normalized": "Governments should prioritize climate change mitigation over economic growth",
                  "violations": [],
                  "reasoning": "The claim is specific and debatable, addressing a significant policy issue.",
                  "categoryId": "economy-environment",
                  "tags": ["Government", "Climate Change", "Environment", "Economy"]
                }
                
                Claim: "The United States has a moral obligation to intervene in international conflicts"
                Response: 
                {
                  "valid": true,
                  "normalized": "The US has a moral obligation to intervene in international conflicts.",
                  "violations": [],
                  "reasoning": "The claim is debatable and pertains to international relations without violating platform rules",
                  "categoryId": "politics",
                  "tags": ["US", "Politics", "War"]
                }
            """.trimIndent()

        return try {
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: ""

            parseValidationResponse(response)
        } catch (e: Exception) {
            logger.error("Error validating claim: ${e.message}", e)
            ClaimValidationResult(
                valid = false,
            )
        }
    }


    private fun parseValidationResponse(response: String): ClaimValidationResult {
        return try {
            objectMapper.readValue<ClaimValidationResult>(response)
        } catch (e: Exception) {
            logger.error("Error parsing validation response: ${e.message}. Response: $response", e)
            // Fallback parsing
            val valid = response.contains("\"valid\": true") || response.contains("\"valid\":true")
            ClaimValidationResult(
                valid = valid,
                normalized = null,
                categoryId = "social-issues-culture",
                reasoning = "Parse error",
            )
        }
    }

    fun generateUsernames(seed: String): List<String> {
        val normalizedSeed = seed.replace(invalidCharsRegex, "").take(15)
        val prompt = """
            Generate 5 funny and memorable usernames for a debating platform.
            Make them short, easy to read, and inspired by debating or argument styles.
            Examples: "LogicNinja", "HotTakeHero", "DevilsAdvocate".

            Seed word: "$normalizedSeed"

            Output only in JSON array format, like this:
            ["Name1", "Name2", "Name3", "Name4", "Name5"]
        """.trimIndent()

        val usernames = try {
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: "[]"

            objectMapper.readValue<List<String>>(response)
        } catch (e: Exception) {
            logger.error("Error generating usernames: ${e.message}", e)
            emptyList()
        }

        return usernames.takeIf { it.isNotEmpty() } ?: listOf(normalizedSeed)
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
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            val requestBody = mapOf(
                "model" to "omni-moderation-latest",
                "input" to message
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(
                moderationUrl,
                request,
                ModerationResponse::class.java
            )

            val result = response?.results?.firstOrNull()
            if (result?.flagged == true) {
                val replacementMessage = MODERATION_REPLACEMENT_MESSAGES.random()
                logger.info("Chat message moderated and replaced with: $replacementMessage")
                ChatModerationResult(
                    message = replacementMessage,
                    wasModerated = true
                )
            } else {
                ChatModerationResult(
                    message = message,
                    wasModerated = false
                )
            }
        } catch (e: Exception) {
            logger.error("Error moderating chat message: ${e.message}", e)
            // Fail open - return original message if moderation check fails
            ChatModerationResult(
                message = message,
                wasModerated = false
            )
        }
    }
}

data class ClaimValidationResult(
    val valid: Boolean,
    val normalized: String? = null,
    val violations: List<String> = emptyList(),
    val reasoning: String? = null,
    val categoryId: String? = null,
    val tags: List<String> = emptyList(),
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

// OpenAI Moderation API response structures
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