package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.claim.model.UserClaimStanceModel
import com.debbly.server.claim.repository.UserClaimStanceRepository
import com.debbly.server.user.UserEntity
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class UserClaimStanceService(
    private val userClaimStanceRepository: UserClaimStanceRepository,
    private val claimRepository: ClaimRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryRepository
) {
    fun save(stances: List<ClaimStanceUpdate>, user: UserEntity) {

        for (stanceInput in stances) {
            val claim = if (stanceInput.claimId != null) {
                claimRepository.findById(stanceInput.claimId).orElseThrow { Exception("Claim not found") }

            } else if (stanceInput.title != null) {
                val politicsCategory = categoryRepository.findById("politics")
                    .orElseThrow { Exception("Politics category not found") }
                val newClaim = ClaimEntity(
                    claimId = idService.getId(),
                    title = stanceInput.title,
                    category = politicsCategory,
                    tags = emptySet()
                )
                claimRepository.save(newClaim)

            } else {
                continue
            }

            // TODO get category from the claim
            val claimStance = UserClaimStanceModel(
                claimId = claim.claimId,
                userId = user.userId,
                stance = stanceInput.stance,
                categoryId = claim.category.categoryId,
                updatedAt = Instant.now()
            )
            userClaimStanceRepository.save(claimStance)
        }
    }

    fun save(update: ClaimStanceUpdate, user: UserEntity) {
        require(update.claimId != null)
        claimRepository.findById(update.claimId).getOrNull()?.let { claim ->
            userClaimStanceRepository.save(
                UserClaimStanceModel(
                    claimId = update.claimId,
                    userId = user.userId,
                    stance = update.stance,
                    categoryId = claim.category.categoryId,
                    updatedAt = Instant.now()
                )
            )
        }
    }


}
