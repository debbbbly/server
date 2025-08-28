package com.debbly.server.claim.user.repository

import com.debbly.server.claim.model.toEntity
import com.debbly.server.claim.model.toModel
import com.debbly.server.claim.user.UserClaimModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class UserClaimCachedRepository(
    private val jpaRepository: UserClaimJpaRepository,
) {
    @Cacheable(value = ["userClaims"], key = "#userId", unless = "#result.isEmpty()")
    fun findByUserId(userId: String): List<UserClaimModel> =
        jpaRepository.findByIdUserId(userId).map { it.toModel() }

    @Caching(
        evict = [
            CacheEvict(value = ["userClaims"], key = "#model.userId")
        ]
    )
    fun save(model: UserClaimModel) {
        jpaRepository.save(model.toEntity())
    }

    private fun UserClaimEntity.toModel(): UserClaimModel {
        return UserClaimModel(
            claim = this.claim.toModel(),
            userId = this.id.userId,
            stance = this.stance,
            priority = this.priority,
            updatedAt = this.updatedAt
        )
    }

    private fun UserClaimModel.toEntity() = UserClaimEntity(
        id = UserClaimId(
            userId = this.userId,
            claimId = this.claim.claimId
        ),
        claim = claim.toEntity(),
        categoryId = this.claim.category.categoryId,
        stance = this.stance,
        priority = this.priority,
        updatedAt = this.updatedAt
    )
}
