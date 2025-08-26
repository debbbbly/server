package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAIService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.exception.ClaimValidationException
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.TagModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
class ClaimService(
    private val claimCachedRepository: ClaimCachedRepository,
    private val categoryCachedRepository: CategoryCachedRepository,
    private val tagRepository: TagRepository,
    private val openAIService: OpenAIService,
    private val idService: IdService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<ClaimModel> = claimCachedRepository.findAll()

    fun save(claim: ClaimModel): ClaimModel = claimCachedRepository.save(claim)

    fun getTopClaims(categoryIds: List<String>?, limit: Int): List<ClaimModel> {
        return (if (categoryIds.isNullOrEmpty()) {
            claimCachedRepository.findAll().take(limit)
        } else {
            claimCachedRepository.findByCategoryCategoryIdIn(categoryIds).take(limit)
        })
            .filter { claim -> claim.category.active }
    }

    @Transactional
    fun propose(title: String, userId: String): ClaimModel {
        logger.info("Processing claim proposal: '$title' by user: $userId")

        // AI validation, normalization, category assignment, and tag generation in one call
        val validationResult = openAIService.validateClaim(title)
        if (!validationResult.valid) {
            logger.warn("Claim rejected for user $userId: ${validationResult.violations}")
            throw ClaimValidationException(validationResult.violations, validationResult.reasoning)
        }

        logger.info("Claim validation passed with category: ${validationResult.categoryId}, tags: ${validationResult.tags}")

        // Get category from AI result
        val categoryId = validationResult.categoryId ?: "social-issues-culture"
        val categoryModel = categoryCachedRepository.findById(categoryId)
            ?: throw IllegalArgumentException("Category not found: $categoryId")

        // Create tags from AI result
        val tags = validationResult.tags.mapNotNull { tagTitle ->
            // Find existing tag or create new one
            tagRepository.findByTitle(tagTitle).getOrNull()?.let { existingTag ->
                TagModel(existingTag.tagId, existingTag.title)
            } ?: try {
                val savedTag = tagRepository.save(TagEntity(idService.getId(), tagTitle))
                TagModel(savedTag.tagId, savedTag.title)
            } catch (e: Exception) {
                logger.warn("Failed to create tag '$tagTitle': ${e.message}")
                null
            }
        }.toSet()

        val newClaim = ClaimModel(
            claimId = idService.getId(),
            category = categoryModel,
            title = validationResult.normalized ?: title,
            tags = tags
        )

        return claimCachedRepository.save(newClaim)
    }
}