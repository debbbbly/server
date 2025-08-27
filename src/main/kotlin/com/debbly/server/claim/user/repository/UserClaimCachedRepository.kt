package com.debbly.server.claim.user.repository

import com.debbly.server.claim.user.UserClaimModel
import org.springframework.stereotype.Service

@Service
class UserClaimCachedRepository(
    private val jpaRepository: UserClaimJpaRepository
) {
    fun findByUserId(userId: String): List<UserClaimModel> =
        jpaRepository.findByIdUserId(userId).map { it.toModel() }

    fun findByIdUserIdAndCategoryIdIn(userId: String, categoryIds: List<String>): List<UserClaimModel> =
        jpaRepository.findByIdUserIdAndCategoryIdIn(userId, categoryIds).map { it.toModel() }

    fun save(model: UserClaimModel) {
        jpaRepository.save(model.toEntity())
    }

    private fun UserClaimEntity.toModel() = UserClaimModel(
        userId = this.id.userId,
        claimId = this.id.claimId,
        categoryId = this.categoryId,
        stance = this.stance,
        priority = this.priority,
        updatedAt = this.updatedAt
    )

    private fun UserClaimModel.toEntity() = UserClaimEntity(
        id = UserClaimId(
            userId = this.userId,
            claimId = this.claimId
        ),
        categoryId = this.categoryId,
        stance = this.stance,
        priority = this.priority,
        updatedAt = this.updatedAt
    )
}
