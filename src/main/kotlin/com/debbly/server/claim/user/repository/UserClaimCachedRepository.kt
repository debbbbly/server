package com.debbly.server.claim.user.repository

import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.model.toEntity
import com.debbly.server.claim.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserClaimCachedRepository(
    private val jpaRepository: UserClaimJpaRepository,
) {
    @Cacheable(value = ["userClaims"], key = "#userId", unless = "#result.isEmpty()")
    fun findByUserId(userId: String): List<UserClaimModel> =
        jpaRepository.findByIdUserId(userId).map { it.toModel() }

    @Cacheable(value = ["userClaim"], key = "#userId + '_' + #claimId")
    fun findByUserIdClaimId(userId: String, claimId: String): UserClaimModel? =
        jpaRepository.findByIdUserIdAndIdClaimId(userId, claimId)?.toModel()

    @Caching(
        evict = [
            CacheEvict(value = ["userClaims"], key = "#model.userId"),
            CacheEvict(value = ["userClaim"], key = "#model.userId + '_' + #model.claim.claimId")
        ]
    )
    fun save(model: UserClaimModel) {
        jpaRepository.save(model.toEntity())
    }

    @Transactional
    @Caching(
        evict = [
            CacheEvict(value = ["userClaims"], key = "#userId"),
            CacheEvict(value = ["userClaim"], key = "#userId + '_' + #claimId")
        ]
    )
    fun deleteByUserIdAndClaimId(userId: String, claimId: String) {
        jpaRepository.deleteByIdUserIdAndIdClaimId(userId, claimId)
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
        categoryId = this.claim.categoryId,
        stance = this.stance,
        priority = this.priority,
        updatedAt = this.updatedAt
    )
}
