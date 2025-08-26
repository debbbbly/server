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

        // Step 1: AI validation
        val validationResult = openAIService.validateClaim(title)
        if (!validationResult.valid) {
            logger.warn("Claim rejected for user $userId: ${validationResult.violations}")
            throw ClaimValidationException(validationResult.violations, validationResult.reasoning)
        }

        logger.info("Claim validation passed with confidence: ${validationResult.confidence}")

        val categoryId = openAIService.assignCategory(title)
        val categoryModel = categoryCachedRepository.findById(categoryId)
            ?: throw IllegalArgumentException("Category not found: $categoryId")

        val tagTitles = openAIService.generateTags(title)
        val tags = tagTitles.mapNotNull { tagTitle ->
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