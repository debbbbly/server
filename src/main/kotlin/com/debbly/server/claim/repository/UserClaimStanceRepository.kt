package com.debbly.server.claim.repository

import com.debbly.server.claim.model.UserClaimStanceModel
import org.springframework.stereotype.Service

@Service
class UserClaimStanceRepository(
    private val userClaimStanceJpaRepository: UserClaimStanceJpaRepository
) {
    fun findByUserId(userId: String): List<UserClaimStanceModel> =
        userClaimStanceJpaRepository.findByIdUserId(userId).map { it.toModel() }

    fun save(model: UserClaimStanceModel) {
        userClaimStanceJpaRepository.save(model.toEntity())
    }

    private fun UserClaimStanceEntity.toModel() = UserClaimStanceModel(
        userId = this.id.userId,
        claimId = this.id.claimId,
        categoryId = this.categoryId,
        stance = this.stance,
        updatedAt = this.updatedAt
    )

    private fun UserClaimStanceModel.toEntity() = UserClaimStanceEntity(
        id = UserClaimStanceId(
            userId = this.userId,
            claimId = this.claimId
        ),
        categoryId = this.categoryId,
        stance = this.stance,
        updatedAt = this.updatedAt
    )
}
