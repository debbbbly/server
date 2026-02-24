package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAiService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.exception.ClaimValidationException
import com.debbly.server.claim.exception.DuplicateClaimException
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.StanceToTopic
import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.embedding.claim.ClaimEmbeddingEntity
import com.debbly.server.embedding.claim.ClaimEmbeddingRepository
import com.debbly.server.util.SlugService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
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
    private val slugService: SlugService,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun save(claim: ClaimModel): ClaimModel = claimCachedRepository.save(claim)

    fun search(query: String, categoryId: String?, limit: Int = 20): List<ClaimModel> {
        if (query.isBlank() || query.length < 2) return emptyList()
        return claimCachedRepository.search(query.trim(), categoryId, limit)
    }

    fun getUserClaims(
        userId: String,
        limit: Int,
    ): List<UserClaimModel> {
        val activeCategoryIds =
            categoryCachedRepository
                .findAll()
                .filter { it.active }
                .map { it.categoryId }
                .toSet()

        return userClaimCachedRepository
            .findByUserId(userId)
            .filter { it.claim.categoryId in activeCategoryIds }
    }

    companion object {
        private const val MODERATION_CATEGORY_ID = "moderation"
        private const val MODERATION_TOPIC_ID = "moderation"
    }

    @Transactional
    fun create(
        title: String,
        userId: String,
        stance: ClaimStance? = null,
    ): ClaimModel {
        logger.info("Processing claim proposal: '$title' by user: $userId")

        // Step 1: Validate and normalize claim
        val validationResult = openAIService.moderateClaim(title)

        if (!validationResult.valid) {
            logger.warn("Claim rejected for user $userId: ${validationResult.violations}")
            throw ClaimValidationException(validationResult.violations, validationResult.reasoning)
        }

        val normalizedTitle = validationResult.normalized ?: title

        // Step 2: Generate embedding immediately for duplicate detection
        val embedding = openAIService.generateEmbedding(normalizedTitle)
        if (embedding == null) {
            logger.error("Failed to generate embedding for claim: $normalizedTitle")
            throw ClaimValidationException(
                listOf("Embedding generation failed"),
                "Failed to process claim for similarity checking."
            )
        }

        // Step 3: Check for duplicates using embedding
        val similarClaims = claimSimilarityService.findSimilarClaimsByEmbedding(
            embedding = embedding,
            limit = 1,
            minSimilarity = 0.95
        )
        val duplicate = similarClaims.firstOrNull()?.takeIf { it.isDuplicate }

        if (duplicate != null) {
            logger.warn("Duplicate claim detected for user $userId: existing claim ${duplicate.claimId}")
            throw DuplicateClaimException(duplicate)
        }

        // Step 4: Create claim with moderation category/topic (will be updated async)
        val claim = ClaimModel(
            claimId = idService.getId(),
            categoryId = MODERATION_CATEGORY_ID,
            title = normalizedTitle,
            slug = slugService.slugify(normalizedTitle),
            createdAt = now(clock),
            topicId = MODERATION_TOPIC_ID,
            stanceToTopic = StanceToTopic.NEUTRAL,
        )
        claimCachedRepository.save(claim)
        logger.info("Claim ${claim.claimId} created with moderation category/topic, awaiting async classification")

        // Step 5: Save embedding
        try {
            val embeddingEntity = ClaimEmbeddingEntity(
                claimId = claim.claimId,
                title = claim.title,
                categoryId = claim.categoryId,
                embedding = embedding.map { it.toFloat() }.toFloatArray(),
                createdAt = claim.createdAt,
            )
            embeddingRepository.save(embeddingEntity)
            logger.info("Embedding saved for claim ${claim.claimId}")
        } catch (e: Exception) {
            logger.error("Error saving embedding for claim ${claim.claimId}: ${e.message}", e)
        }

        // Step 6: Save user stance if provided
        stance?.let {
            userClaimCachedRepository.save(
                UserClaimModel(
                    claim = claim,
                    userId = userId,
                    stance = it,
                    priority = null,
                    updatedAt = now(clock),
                ),
            )
        }

        extractTopicAsync(claim.claimId)

        return claim
    }

    /**
     * Async method to extract topic and category for a claim.
     * Updates the claim with the extracted values.
     * If extraction fails, claim remains in moderation category/topic.
     */
    @Async
    fun extractTopicAsync(claimId: String) {
        try {
            val claim = claimCachedRepository.findById(claimId)
            if (claim == null) {
                logger.error("Claim $claimId not found for async topic extraction")
                return
            }

            logger.info("Starting async topic extraction for claim $claimId: '${claim.title}'")

            val extraction = openAIService.extractTopicAndCategory(claim.title)
            logger.info("Extraction result for claim $claimId: category=${extraction.categoryId}, topic='${extraction.topic}', stance=${extraction.stanceToTopic}")

            // Validate category exists
            val categoryId = extraction.categoryId
            if (categoryCachedRepository.findById(categoryId) == null) {
                logger.error("Category not found for claim $claimId: $categoryId, keeping in moderation")
                return
            }

            val topic = topicService.findOrCreateTopic(extraction.topic, categoryId)
            logger.info("Claim $claimId associated with topic ${topic.topicId}: '${topic.title}'")

            // Update claim with extracted topic and category
            val updatedClaim = claim.copy(
                categoryId = topic.categoryId,
                topicId = topic.topicId,
                stanceToTopic = extraction.stanceToTopic
            )
            claimCachedRepository.save(updatedClaim)

            // Update embedding categoryId as well
            try {
                embeddingRepository.updateCategoryId(claimId, topic.categoryId)
                logger.info("Updated embedding categoryId for claim $claimId")
            } catch (e: Exception) {
                logger.error("Error updating embedding categoryId for claim $claimId: ${e.message}", e)
            }

            logger.info("Async topic extraction completed for claim $claimId: category=${topic.categoryId}, topic=${topic.topicId}")

        } catch (e: Exception) {
            logger.error("Error in async topic extraction for claim $claimId: ${e.message}", e)
            // Claim remains in moderation category/topic
        }
    }

    @Transactional
    fun reclassifyClaim(claim: ClaimModel): ReclassifyResult {
        logger.info("Reclassifying claim ${claim.claimId}: '${claim.title}'")

        val extraction = openAIService.extractTopicAndCategory(claim.title)
        logger.info("Extraction result for claim ${claim.claimId}: category=${extraction.categoryId}, topic='${extraction.topic}', stance=${extraction.stanceToTopic}")

        // Validate category exists
        val categoryId = extraction.categoryId
        categoryCachedRepository.findById(categoryId)
            ?: throw IllegalArgumentException("Category not found: $categoryId")

        val topic = topicService.findOrCreateTopic(extraction.topic, categoryId)

        logger.info("Claim ${claim.claimId} associated with topic ${topic.topicId}: '${topic.title}'")

        // Update claim with new topic and category
        val updatedClaim = claim.copy(
            categoryId = topic.categoryId,
            topicId = topic.topicId,
            stanceToTopic = extraction.stanceToTopic
        )
        claimCachedRepository.save(updatedClaim)

        return ReclassifyResult(
            newCategoryId = topic.categoryId,
            newTopicId = topic.topicId,
            newTopicTitle = topic.title,
            newOriginalTopicTitle = extraction.topic,
            stanceToTopic = extraction.stanceToTopic
        )
    }
}

data class ReclassifyResult(
    val newCategoryId: String,
    val newTopicId: String,
    val newTopicTitle: String,
    val newOriginalTopicTitle: String,
    val stanceToTopic: StanceToTopic
)
