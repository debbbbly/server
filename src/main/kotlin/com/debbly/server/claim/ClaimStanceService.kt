package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ClaimStanceService(
    private val claimStanceRepository: ClaimStanceRepository,
    private val claimRepository: ClaimRepository,
    private val userRepository: UserRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryRepository
) {
    fun processStances(stances: List<ClaimStanceUpdate>, externalUserId: String) {
        val user = userRepository.findByExternalUserId(externalUserId).orElseThrow { Exception("User not found") }

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
                    categories = setOf(politicsCategory),
                    tags = emptySet()
                )
                val savedClaim = claimRepository.save(newClaim)

                UserClaimStanceId(savedClaim.claimId, user.userId)
            } else {
                continue
            }

            val claimStance = UserClaimStanceEntity(
                id = userClaimStanceId,
                stance = stanceInput.stance,
                updatedAt = Instant.now()
            )
            claimStanceRepository.save(claimStance)
        }
    }
}
