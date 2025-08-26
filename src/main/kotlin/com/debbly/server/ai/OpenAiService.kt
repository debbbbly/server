package com.debbly.server.ai

import com.debbly.server.category.repository.CategoryCachedRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class OpenAIService(
    private val chatClient: ChatClient,
    private val categoryRepository: CategoryCachedRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

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
            - Correct spelling/grammar
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
            - Assign 1–2 subject tags (entities, groups, or concrete topics; Title Case; 1–3 words).
            - Assign 1–2 domain tags (broad thematic areas: "Technology", "Law & Justice", "Economics", "Health", "Education", etc.).
            - Optionally assign 0–1 sensitivity tag if applicable: ("Conspiracy", "Drugs", "Sex", "Violence", "Religion", "Controversial", etc.).
            - Tags must not duplicate the main category. Tags must be distinct from one another.

            Instructions:
            For the given user claim: "$title"

            1. Check if the claim follows ALL platform rules.
            2. If invalid, list each violated rule explicitly.
            3. Assign exactly ONE main category.
            4. Assign 2–5 tags (subject, domain, and sensitivity).
            5. Respond ONLY with a single valid JSON object. No text outside JSON.
            6. Reasoning must be concise (1–2 sentences).

            Output Format:
            {
              "valid": true/false,
              "violations": ["string", "string"],
              "reasoning": "short explanation (1–2 sentences)",
              "normalized": "cleaned-up version (empty string if invalid)",
              "categoryId": "string",
              "tags": ["string", "string", "string", "string"]
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
                violations = emptyList(),
                reasoning = "AI validation failed, rejecting claim",
                normalized = null,
                confidence = 0.0
            )
        }
    }

    fun generateTags(title: String): List<String> {
        val prompt = """
        Generate 2-4 tags for this debate claim following these guidelines:
        - Tags should be breoad enough that multiple claims can share them
        - Tags should be specific enough to be meaningful
        - Use title case (e.g., "AI Regulation" not "ai regulation")
        - Focus on the main topics/domains involved
        - Avoid overly generic tags like "Politics" or "Technology"
        
        Claim: "$title"
        
        Respond ONLY with a JSON array of tag strings: ["Tag1", "Tag2", "Tag3"]
        """.trimIndent()

        return try {
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: ""

            parseTagsResponse(response)
        } catch (e: Exception) {
            logger.error("Error generating tags: ${e.message}", e)
            listOf("General")
        }
    }

    fun assignCategory(title: String): String {
        val prompt = """
        Classify this debate claim into ONE of these predefined categories:
        
        - social-issues-culture: Social topics, cultural issues, ethics, human rights
        - economy-environment: Economic policy, environmental issues, business, sustainability  
        - sports-entertainment-lifestyle: Sports, entertainment, lifestyle, health, fitness
        - technology-innovation: Technology, AI, innovation, science, digital topics
        - politics: Political topics, governance, elections, policy
        
        Claim: "$title"
        
        Respond with ONLY the category ID (no quotes): social-issues-culture
        """.trimIndent()

        return try {
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content() ?: ""

            parseCategoryResponse(response)
        } catch (e: Exception) {
            logger.error("Error assigning category: ${e.message}", e)
            "social-issues-culture" // Default fallback
        }
    }

    private fun parseValidationResponse(response: String): ClaimValidationResult {
        return try {
            val jsonResponse = objectMapper.readValue<ValidationResponse>(response)
            ClaimValidationResult(
                valid = jsonResponse.valid,
                violations = jsonResponse.violations,
                reasoning = jsonResponse.reasoning,
                confidence = jsonResponse.confidence,
                normalized = jsonResponse.normalized
            )
        } catch (e: Exception) {
            logger.error("Error parsing validation response: ${e.message}. Response: $response", e)
            // Fallback parsing
            val valid = response.contains("\"valid\": true") || response.contains("\"valid\":true")
            ClaimValidationResult(valid = valid, violations = emptyList(), reasoning = "Parse error", confidence = 0.0, normalized = null)
        }
    }

    private fun parseTagsResponse(response: String): List<String> {
        return try {
            objectMapper.readValue<List<String>>(response.trim())
        } catch (e: Exception) {
            logger.error("Error parsing tags response: ${e.message}. Response: $response", e)
            // Fallback parsing
            try {
                val tagsStr = response.substringAfter("[").substringBefore("]")
                tagsStr.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
            } catch (e2: Exception) {
                listOf("General")
            }
        }
    }

    private fun parseCategoryResponse(response: String): String {
        val category = response.trim().removeSurrounding("\"")
        val validCategories = setOf(
            "social-issues-culture",
            "economy-environment",
            "sports-entertainment-lifestyle",
            "technology-innovation",
            "politics"
        )
        return if (category in validCategories) category else "social-issues-culture"
    }
}

private data class ValidationResponse(
    val valid: Boolean,
    val violations: List<String>,
    val reasoning: String,
    val confidence: Double,
    val normalized: String
)

data class ClaimValidationResult(
    val valid: Boolean,
    val violations: List<String>,
    val reasoning: String,
    val confidence: Double,
    val normalized: String?
)