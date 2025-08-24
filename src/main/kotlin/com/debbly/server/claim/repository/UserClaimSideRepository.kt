package com.debbly.server.claim.repository

import com.debbly.server.claim.model.UserClaimSideModel
import org.springframework.stereotype.Service

@Service
class UserClaimSideRepository(
    private val userClaimSideJpaRepository: UserClaimSideJpaRepository
) {
    fun findByUserId(userId: String): List<UserClaimSideModel> =
        userClaimSideJpaRepository.findByIdUserId(userId).map { it.toModel() }

    fun save(model: UserClaimSideModel) {
        userClaimSideJpaRepository.save(model.toEntity())
    }

    private fun UserClaimSideEntity.toModel() = UserClaimSideModel(
        userId = this.id.userId,
        claimId = this.id.claimId,
        categoryId = this.categoryId,
        side = this.side,
        updatedAt = this.updatedAt
    )

    private fun UserClaimSideModel.toEntity() = UserClaimSideEntity(
        id = UserClaimSideId(
            userId = this.userId,
            claimId = this.claimId
        ),
        categoryId = this.categoryId,
        side = this.side,
        updatedAt = this.updatedAt
    )
}
