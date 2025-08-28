package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.user.model.UserModel
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserClaimService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val claimRepository: ClaimCachedRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryCachedRepository
) {
    fun save(userClaimUpdates: List<ClaimStanceUpdate>, user: UserModel) {

        for (update in userClaimUpdates) {
            val claim = if (update.claimId != null) {
                claimRepository.getById(update.claimId)

            } else if (update.title != null) {
                val politicsCategory = categoryRepository.getById("politics")
                val newClaim = ClaimModel(
                    claimId = idService.getId(),
                    title = update.title,
                    category = politicsCategory,
                    tags = emptySet(),
                    popularity = 0
                )
                claimRepository.save(newClaim)

            } else {
                continue
            }

            val userClaim = UserClaimModel(
                claim = claim,
                userId = user.userId,
                stance = update.stance,
                priority = 0,
                updatedAt = Instant.now()
            )
            userClaimRepository.save(userClaim)
        }
    }

    fun save(update: ClaimStanceUpdate, user: UserModel) {
        require(update.claimId != null)
        claimRepository.findById(update.claimId)?.let { claim ->
            userClaimRepository.save(
                UserClaimModel(
                    claim = claim,
                    userId = user.userId,
                    stance = update.stance,
                    priority = 0,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    fun updatePriorities(priorityUpdates: List<PriorityUpdate>, user: UserModel) {
        for (update in priorityUpdates) {
            claimRepository.findById(update.claimId)?.let { claim ->
                userClaimRepository.save(
                    UserClaimModel(
                        claim = claim,
                        userId = user.userId,
                        stance = userClaimRepository.findByUserId(user.userId)
                            .find { it.claim.claimId == update.claimId }?.stance ?: ClaimStance.EITHER,
                        priority = update.priority,
                        updatedAt = Instant.now()
                    )
                )
            }
        }
    }

}
