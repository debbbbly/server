package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAiService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.exception.ClaimValidationException
import com.debbly.server.claim.exception.DuplicateClaimException
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.embedding.claim.ClaimEmbeddingEntity
import com.debbly.server.embedding.claim.ClaimEmbeddingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.Instant.now

@Service
class ClaimService(
    private val claimCachedRepository: ClaimCachedRepository,
    private val categoryCachedRepository: CategoryCachedRepository,
    private val openAIService: OpenAiService,
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val claimSimilarityService: ClaimSimilarityService,
    private val embeddingRepository: ClaimEmbeddingRepository,
    private val topicService: com.debbly.server.claim.topic.TopicService,
    private val idService: IdService,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<ClaimModel> = claimCachedRepository.findAll()

    fun save(claim: ClaimModel): ClaimModel = claimCachedRepository.save(claim)

    fun getUserClaims(userId: String, limit: Int): List<UserClaimModel> {
        val activeCategoryIds = categoryCachedRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        return userClaimCachedRepository.findByUserId(userId)
            .filter { it.claim.categoryId in activeCategoryIds }
    }


    @Transactional
    fun create(title: String, userId: String, stance: ClaimStance? = null): ClaimModel {
        logger.info("Processing claim proposal: '$title' by user: $userId")

        val validationResult = openAIService.moderateClaim(title)

        if (!validationResult.valid) {
            logger.warn("Claim rejected for user $userId: ${validationResult.violations}")
            throw ClaimValidationException(validationResult.violations, validationResult.reasoning)
        }

        // logger.info("Claim validation passed with category: ${validationResult.categoryId}")

        val normalizedTitle = validationResult.normalized ?: title
        val duplicate = claimSimilarityService.findDuplicate(normalizedTitle)
        if (duplicate != null) {
            logger.warn("Duplicate claim detected for user $userId: existing claim ${duplicate.claimId}")
            throw DuplicateClaimException(duplicate)
        }

        // Topic is required for all valid claims
        if (validationResult.topic.isNullOrBlank()) {
            throw ClaimValidationException(
                listOf("Topic extraction failed"),
                "AI failed to extract a topic from the claim. This should not happen for valid claims."
            )
        }

        val suggestedCategoryId = validationResult.categoryId ?: "society"
        categoryCachedRepository.findById(suggestedCategoryId)
            ?: throw IllegalArgumentException("Category not found: $suggestedCategoryId")

        val topic = topicService.findOrCreateTopic(validationResult.topic, suggestedCategoryId)
        logger.info("Claim associated with topic ${topic.topicId}: '${topic.title}' (category: ${topic.categoryId})")

        val categoryId = topic.categoryId
        categoryCachedRepository.findById(categoryId)
            ?: throw IllegalArgumentException("Topic's category not found: $categoryId")

        val claim = ClaimModel(
            claimId = idService.getId(),
            categoryId = categoryId,
            title = validationResult.normalized ?: title,
            popularity = 0,
            createdAt = now(clock),
            topicId = topic.topicId,
            topicStance = validationResult.stance
        )
        claimCachedRepository.save(claim)

        try {
            val embedding = openAIService.generateEmbedding(claim.title)
            if (embedding != null) {
                val embeddingEntity = ClaimEmbeddingEntity(
                    claimId = claim.claimId,
                    title = claim.title,
                    categoryId = claim.categoryId,
                    embedding = embedding.map { it.toFloat() }.toFloatArray(),
                    createdAt = claim.createdAt
                )
                embeddingRepository.save(embeddingEntity)
                logger.info("Embedding generated and saved to pgvector DB for claim ${claim.claimId}")
            } else {
                logger.warn("Failed to generate embedding for claim ${claim.claimId}")
            }
        } catch (e: Exception) {
            logger.error("Error generating/saving embedding for claim ${claim.claimId}: ${e.message}", e)
        }

        stance?.let {
            userClaimCachedRepository.save(
                UserClaimModel(
                    claim = claim,
                    userId = userId,
                    stance = it,
                    priority = null, // TODO set highest
                    updatedAt = now(clock)
                )
            )
        }

        return claim
    }

}
