package com.debbly.server.claim.repository

import com.debbly.server.claim.model.UserClaimStanceModel
import org.springframework.stereotype.Service

@Service
class UserClaimStanceRepository(
    private val userClaimStanceJpaRepository: UserClaimStanceJpaRepository
) {
    fun findByUserId(userId: String): List<UserClaimStanceModel> =
        userClaimStanceJpaRepository.findByIdUserId(userId).map { it.toModel() }

    private fun UserClaimStanceEntity.toModel() = UserClaimStanceModel(
        userId = this.id.userId,
        claimId = this.id.claimId,
        categoryId = this.categoryId,
        stance = this.stance,
        updatedAt = this.updatedAt
    )
}
