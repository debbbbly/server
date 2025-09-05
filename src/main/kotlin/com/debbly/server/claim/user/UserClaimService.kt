package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.UserClaimModel
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

    //    fun getUserClaimsWithUserData(userId: String, limit: Int): List<ClaimWithUserDataModel> {
//        val activeCategoryIds = categoryRepository.findAll()
//            .filter { it.active }
//            .map { it.categoryId }
//            .toSet()
//
//        return userClaimRepository.findByUserId(userId)
//            .filter { it.claim.category.categoryId in activeCategoryIds }
//            .map { userClaim ->
//                ClaimWithUserDataModel(
//                    claimId = userClaim.claim.claimId,
//                    category = userClaim.claim.category,
//                    title = userClaim.claim.title,
//                    tags = userClaim.claim.tags,
//                    popularity = userClaim.claim.popularity,
//                    user = UserClaimDataModel(
//                        userId = userClaim.userId,
//                        stance = userClaim.stance,
//                        priority = userClaim.priority,
//                        updatedAt = userClaim.updatedAt
//                    )
//                )
//            }
//    }
    fun getClaims(userId: String) = userClaimRepository.findByUserId(userId)

    fun updateStance(userId: String, claimId: String, stance: ClaimStance) {
        userClaimRepository.findByUserIdClaimId(userId, claimId)?.let { userClaim ->
            userClaimRepository.save(userClaim.copy(stance = stance))
        } ?: let {

            claimRepository.findById(claimId)?.let { claim ->
                userClaimRepository.save(
                    UserClaimModel(
                        claim = claim,
                        userId = userId,
                        stance = stance,
                        priority = null,
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

    fun removeStance(userId: String, claimId: String) {
        userClaimRepository.deleteByUserIdAndClaimId(userId, claimId)
    }

}
