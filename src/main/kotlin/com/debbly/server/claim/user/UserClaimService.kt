package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class UserClaimService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val claimRepository: ClaimCachedRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryCachedRepository,
    private val clock: Clock
) {

    fun getClaims(userId: String) = userClaimRepository.findByUserId(userId)

    fun updateStance(userId: String, claimId: String, stance: ClaimStance?) {
        userClaimRepository.findByUserIdClaimId(userId, claimId)?.let { userClaim ->
            if (stance == null) {
                userClaimRepository.deleteByUserIdAndClaimId(userId, claimId)
            } else {
                userClaimRepository.save(userClaim.copy(stance = stance))
            }
        } ?: let {
            stance?.let { stance ->
                claimRepository.findById(claimId)?.let { claim ->
                    userClaimRepository.save(
                        UserClaimModel(
                            claim = claim,
                            userId = userId,
                            stance = stance,
                            priority = null,
                            updatedAt = Instant.now(clock)
                        )
                    )
                }
            }
        }
    }
}
