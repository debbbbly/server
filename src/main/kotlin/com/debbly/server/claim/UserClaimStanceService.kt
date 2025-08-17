package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.claim.repository.UserClaimStanceEntity
import com.debbly.server.claim.repository.UserClaimStanceId
import com.debbly.server.claim.repository.UserClaimStanceJpaRepository
import com.debbly.server.user.repository.UserJpaRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UserClaimStanceService(
    private val userClaimStanceRepository: UserClaimStanceJpaRepository,
    private val claimRepository: ClaimRepository,
    private val userJpaRepository: UserJpaRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryRepository
) {
    fun processStances(stances: List<ClaimStanceUpdate>, externalUserId: String) {
        val user = userJpaRepository.findByExternalUserId(externalUserId).orElseThrow { Exception("User not found") }

        for (stanceInput in stances) {
            val userClaimStanceId = if (stanceInput.claimId != null) {
                val claim = claimRepository.findById(stanceInput.claimId).orElseThrow { Exception("Claim not found") }
                UserClaimStanceId(claim.claimId, user.userId)

            } else if (stanceInput.title != null) {
                val politicsCategory =
                    categoryRepository.findById("politics").orElseThrow { Exception("Politics category not found") }
                val newClaim = ClaimEntity(
                    claimId = idService.getId(),
                    title = stanceInput.title,
                    category = politicsCategory,
                    tags = emptySet()
                )
                val savedClaim = claimRepository.save(newClaim)

                UserClaimStanceId(savedClaim.claimId, user.userId)
            } else {
                continue
            }

            // TODO get category from the claim
            val claimStance = UserClaimStanceEntity(
                id = userClaimStanceId,
                stance = stanceInput.stance,
                categoryId = "politics",
                updatedAt = Instant.now()
            )
            userClaimStanceRepository.save(claimStance)
        }
    }
}
