package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryJpaRepository
import com.debbly.server.claim.model.UserClaimSideModel
import com.debbly.server.claim.repository.UserClaimSideRepository
import com.debbly.server.user.model.UserModel
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class UserClaimSideService(
    private val userClaimSideRepository: UserClaimSideRepository,
    private val claimRepository: ClaimRepository,
    private val idService: IdService,
    private val categoryJpaRepository: CategoryJpaRepository
) {
    fun save(sides: List<ClaimSideUpdate>, user: UserModel) {

        for (sideInput in sides) {
            val claim = if (sideInput.claimId != null) {
                claimRepository.findById(sideInput.claimId).orElseThrow { Exception("Claim not found") }

            } else if (sideInput.title != null) {
                val politicsCategory = categoryJpaRepository.findById("politics")
                    .orElseThrow { Exception("Politics category not found") }
                val newClaim = ClaimEntity(
                    claimId = idService.getId(),
                    title = sideInput.title,
                    category = politicsCategory,
                    tags = emptySet()
                )
                claimRepository.save(newClaim)

            } else {
                continue
            }

            // TODO get category from the claim
            val claimSide = UserClaimSideModel(
                claimId = claim.claimId,
                userId = user.userId,
                side = sideInput.side,
                categoryId = claim.category.categoryId,
                updatedAt = Instant.now()
            )
            userClaimSideRepository.save(claimSide)
        }
    }

    fun save(update: ClaimSideUpdate, user: UserModel) {
        require(update.claimId != null)
        claimRepository.findById(update.claimId).getOrNull()?.let { claim ->
            userClaimSideRepository.save(
                UserClaimSideModel(
                    claimId = update.claimId,
                    userId = user.userId,
                    side = update.side,
                    categoryId = claim.category.categoryId,
                    updatedAt = Instant.now()
                )
            )
        }
    }


}
