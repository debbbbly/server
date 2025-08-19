package com.debbly.server.claim

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.UserClaimStanceEntity
import com.debbly.server.claim.repository.UserClaimStanceId
import com.debbly.server.claim.repository.UserClaimStanceJpaRepository
import com.debbly.server.user.UserEntity
import com.debbly.server.user.repository.UserJpaRepository
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class UserClaimStanceService(
    private val userClaimStanceRepository: UserClaimStanceJpaRepository,
    private val claimRepository: ClaimRepository,
    private val userJpaRepository: UserJpaRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryRepository
) {
    fun save(stances: List<ClaimStanceUpdate>, user: UserEntity, ) {

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

    fun save(update: ClaimStanceUpdate, user: UserEntity) {
        require(update.claimId != null)
        claimRepository.findById(update.claimId).getOrNull()?.let { claim ->
            userClaimStanceRepository.save(
                UserClaimStanceEntity(
                    id = UserClaimStanceId(update.claimId, user.userId),
                    stance = update.stance,
                    categoryId = claim.claimId,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    fun switchStance(claimId: String, stance: ClaimStance?, userId: String) {
        val user = userJpaRepository.findById(userId).orElseThrow { Exception("User not found") }
        val claim = claimRepository.findById(claimId).orElseThrow { Exception("Claim not found") }

        val oppositeStance = when (stance) {
            ClaimStance.PRO -> ClaimStance.CON
            ClaimStance.CON -> ClaimStance.PRO
            else -> stance
        }

        if(oppositeStance == stance) {
            return
        }

        val userClaimStanceId = UserClaimStanceId(claim.claimId, user.userId)

        val claimStance = UserClaimStanceEntity(
            id = userClaimStanceId,
            stance = oppositeStance!!,
            categoryId = claim.category.categoryId,
            updatedAt = Instant.now()
        )
        userClaimStanceRepository.save(claimStance)
    }
}
