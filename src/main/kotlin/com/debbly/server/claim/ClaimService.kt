package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAIService
import com.debbly.server.category.model.toEntity
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.exception.ClaimValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Service
class ClaimService(
    private val repository: ClaimRepository,
    private val categoryCachedRepository: CategoryCachedRepository,
    private val tagRepository: TagRepository,
    private val openAIService: OpenAIService,
    private val idService: IdService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<ClaimEntity> = repository.findAllWithAllData()

    fun save(claim: ClaimEntity): ClaimEntity = repository.save(claim)

    fun getTopClaims(categoryIds: List<String>?, limit: Int): List<ClaimEntity> {
        return (if (categoryIds.isNullOrEmpty()) {
            repository.findAllWithAllData().take(limit)
        } else {
            repository.findByCategoryCategoryIdInWithAllData(categoryIds).take(limit)
        })
            .filter { claim -> claim.category.active }
    }

    @Transactional
    fun propose(title: String, userId: String): ClaimEntity {
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
            tagRepository.findByTitle(tagTitle).getOrNull()
                ?: try {
                    tagRepository.save(TagEntity(idService.getId(), tagTitle))
                } catch (e: Exception) {
                    logger.warn("Failed to create tag '$tagTitle': ${e.message}")
                    null
                }
        }.toSet()

        val newClaim = ClaimEntity(
            claimId = idService.getId(),
            category = categoryModel.toEntity(),
            title = title,
            tags = tags
        )

        val savedClaim = repository.save(newClaim)
        logger.info("Successfully created claim: ${savedClaim.claimId} with ${tags.size} tags")
        
        return savedClaim
    }
}