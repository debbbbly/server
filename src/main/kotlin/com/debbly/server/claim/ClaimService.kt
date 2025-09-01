package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.ai.OpenAIService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.exception.ClaimValidationException
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.TagModel
import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimProposalEntity
import com.debbly.server.claim.repository.ClaimProposalJpaRepository
import com.debbly.server.claim.tag.TagEntity
import com.debbly.server.claim.tag.TagRepository

import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class ClaimService(
    private val claimCachedRepository: ClaimCachedRepository,
    private val categoryCachedRepository: CategoryCachedRepository,
    private val tagRepository: TagRepository,
    private val openAIService: OpenAIService,
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val claimProposalRepository: ClaimProposalJpaRepository,
    private val idService: IdService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<ClaimModel> = claimCachedRepository.findAll()

    fun save(claim: ClaimModel): ClaimModel = claimCachedRepository.save(claim)

    fun getTopClaims(limit: Int): List<ClaimModel> {
        return claimCachedRepository.findAll().take(limit)
            .filter { claim -> claim.category.active }
    }

    fun getUserClaims(userId: String, limit: Int): List<UserClaimModel> {
        val activeCategoryIds = categoryCachedRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        return userClaimCachedRepository.findByUserId(userId)
            .filter { it.claim.category.categoryId in activeCategoryIds }
    }


    @Transactional
    fun propose(title: String, userId: String, stance: ClaimStance? = null): ClaimModel {
        logger.info("Processing claim proposal: '$title' by user: $userId")

        val validationResult = openAIService.validateClaim(title)

        if (!validationResult.valid) {
            saveProposal(
                proposalId = idService.getId(),
                userId = userId,
                originalTitle = title,
                validationResult = validationResult,
                requestedStance = stance,
                resultingClaimId = null
            )

            logger.warn("Claim rejected for user $userId: ${validationResult.violations}")
            throw ClaimValidationException(validationResult.violations, validationResult.reasoning)
        }

        logger.info("Claim validation passed with category: ${validationResult.categoryId}, tags: ${validationResult.tags}")

        val categoryId = validationResult.categoryId ?: "social-issues-culture"
        val categoryModel = categoryCachedRepository.findById(categoryId)
            ?: throw IllegalArgumentException("Category not found: $categoryId")

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
        }.toList()

        val claim = ClaimModel(
            claimId = idService.getId(),
            category = categoryModel,
            title = validationResult.normalized ?: title,
            tags = tags,
            popularity = 0,
            createdAt = Instant.now()
        )
        claimCachedRepository.save(claim)

        saveProposal(
            proposalId = idService.getId(),
            userId = userId,
            originalTitle = title,
            validationResult = validationResult,
            requestedStance = stance,
            resultingClaimId = claim.claimId
        )

        stance?.let {
            userClaimCachedRepository.save(
                UserClaimModel(
                    claim = claim,
                    userId = userId,
                    stance = it,
                    priority = null, // TODO set highest
                    updatedAt = Instant.now()
                )
            )
        }

        return claim
    }

    private fun saveProposal(
        proposalId: String,
        userId: String,
        originalTitle: String,
        validationResult: com.debbly.server.ai.ClaimValidationResult,
        requestedStance: ClaimStance?,
        resultingClaimId: String?
    ) {
        claimProposalRepository.save(
            ClaimProposalEntity(
                proposalId = proposalId,
                userId = userId,
                originalTitle = originalTitle,
                normalizedTitle = validationResult.normalized,
                isValid = validationResult.valid,
                failureReasons = if (!validationResult.valid) validationResult.violations.joinToString("; ") else null,
                reasoning = validationResult.reasoning,
                categoryId = validationResult.categoryId,
                tags = validationResult.tags.joinToString(","),
                userStance = requestedStance,
                claimId = resultingClaimId,
                createdAt = Instant.now()
            )
        )
    }
}