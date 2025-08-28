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
                  "reasoning": "The claim is clear, specific, and debatable without violating any platform rules.",
                  "categoryId": "technology-innovation",
                  "tags": ["AI", "Technology", "Society"]
                }
                
                Claim: "Governments should prioritize climate change mitigation over economic growth"
                Response: 
                {
                  "valid": true,
                  "normalized": "Governments should prioritize climate change mitigation over economic growth.",
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
                  "reasoning": "The claim is debatable and pertains to international relations without violating platform rules.",
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

}

data class ClaimValidationResult(
    val valid: Boolean,
    val normalized: String? = null,
    val violations: List<String> = emptyList(),
    val reasoning: String? = null,
    val categoryId: String? = null,
    val tags: List<String> = emptyList(),
)