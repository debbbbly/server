package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserClaimService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val claimRepository: ClaimCachedRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryCachedRepository
) {

    fun updateUserClaimStance(userId: String, claimId: String, stance: ClaimStance) {
        userClaimRepository.findByUserIdClaimId(userId, claimId)?.let { userClaim ->
            userClaimRepository.save(userClaim.copy(stance = stance))
        } ?: {
            val priority = userClaimRepository.findByUserId(userId)
                .mapNotNull { it.priority }
                .maxOrNull() ?: 0

            claimRepository.findById(claimId)?.let { claim ->
                userClaimRepository.save(
                    UserClaimModel(
                        claim = claim,
                        userId = userId,
                        stance = stance,
                        priority = priority,
                        updatedAt = Instant.now()
                    )
                )
            }
        }
    }

    fun updatePriorities(userId: String, priorities: List<Pair<String, Int>>) {
        for ((claimId, priority) in priorities) {
            claimRepository.findById(claimId)?.let { claim ->
                userClaimRepository.save(
                    UserClaimModel(
                        claim = claim,
                        userId = userId,
                        stance = userClaimRepository.findByUserId(userId)
                            .find { it.claim.claimId == claimId }?.stance ?: ClaimStance.EITHER,
                        priority = priority,
                        updatedAt = Instant.now()
                    )
                )
            }
        }
    }

    fun removeUserClaim(userId: String, claimId: String) {
        userClaimRepository.deleteByUserIdAndClaimId(userId, claimId)
    }

}
