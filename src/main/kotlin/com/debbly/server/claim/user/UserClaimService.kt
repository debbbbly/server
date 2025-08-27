package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryJpaRepository
import com.debbly.server.claim.repository.ClaimEntity
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.user.model.UserModel
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class UserClaimService(
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val claimRepository: ClaimJpaRepository,
    private val idService: IdService,
    private val categoryJpaRepository: CategoryJpaRepository
) {
    fun save(userClaimUpdates: List<ClaimStanceUpdate>, user: UserModel) {

        for (update in userClaimUpdates) {
            val claim = if (update.claimId != null) {
                claimRepository.findById(update.claimId).orElseThrow { Exception("Claim not found") }

            } else if (update.title != null) {
                val politicsCategory = categoryJpaRepository.findById("politics")
                    .orElseThrow { Exception("Politics category not found") }
                val newClaim = ClaimEntity(
                    claimId = idService.getId(),
                    title = update.title,
                    category = politicsCategory,
                    tags = emptySet()
                )
                claimRepository.save(newClaim)

            } else {
                continue
            }

            // TODO get category from the claim
            val userClaim = UserClaimModel(
                claimId = claim.claimId,
                userId = user.userId,
                stance = update.stance,
                categoryId = claim.category.categoryId,
                priority = 0,
                updatedAt = Instant.now()
            )
            userClaimCachedRepository.save(userClaim)
        }
    }

    fun save(update: ClaimStanceUpdate, user: UserModel) {
        require(update.claimId != null)
        claimRepository.findById(update.claimId).getOrNull()?.let { claim ->
            userClaimCachedRepository.save(
                UserClaimModel(
                    claimId = update.claimId,
                    userId = user.userId,
                    stance = update.stance,
                    categoryId = claim.category.categoryId,
                    priority = 0,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    fun updatePriorities(priorityUpdates: List<PriorityUpdate>, user: UserModel) {
        for (update in priorityUpdates) {
            claimRepository.findById(update.claimId).getOrNull()?.let { claim ->
                userClaimCachedRepository.save(
                    UserClaimModel(
                        claimId = update.claimId,
                        userId = user.userId,
                        stance = userClaimCachedRepository.findByUserId(user.userId)
                            .find { it.claimId == update.claimId }?.stance ?: ClaimStance.EITHER,
                        categoryId = claim.category.categoryId,
                        priority = update.priority,
                        updatedAt = Instant.now()
                    )
                )
            }
        }
    }


}
