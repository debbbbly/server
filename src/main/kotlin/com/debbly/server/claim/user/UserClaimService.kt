package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import kotlin.random.Random

@Service
class UserClaimService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val claimRepository: ClaimCachedRepository,
    private val idService: IdService,
    private val categoryRepository: CategoryCachedRepository,
    private val userCachedRepository: UserCachedRepository,
    private val clock: Clock
) {
    companion object {
        val FAKE_USER_IDS: List<String> = (1..30).map { "fake$it" }
    }

    fun getClaims(userId: String) = userClaimRepository.findByUserId(userId)

    fun updateStance(userId: String, claimId: String, stance: ClaimStance?) {
        userClaimRepository.findByUserIdClaimId(userId, claimId)?.let { userClaim ->
            if (stance == null) {
                userClaimRepository.deleteByUserIdAndClaimId(userId, claimId)
            } else {
                userClaimRepository.save(userClaim.copy(stance = stance))
            }
        } ?: let {
            stance?.let { stance ->
                claimRepository.findById(claimId)?.let { claim ->
                    userClaimRepository.save(
                        UserClaimModel(
                            claim = claim,
                            userId = userId,
                            stance = stance,
                            priority = null,
                            updatedAt = Instant.now(clock)
                        )
                    )
                }
            }
        }
    }

    @Transactional
    fun initializeFakeStances(claimId: String): List<UserClaimModel> {
        val claim = claimRepository.findById(claimId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found")

        val fakeUsersById = userCachedRepository.findByIds(FAKE_USER_IDS)
        val missingUserIds = FAKE_USER_IDS.filterNot(fakeUsersById::containsKey)
        if (missingUserIds.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Missing fake users: ${missingUserIds.joinToString(", ")}"
            )
        }

        val randomizedStances = buildList {
            repeat(10) { add(ClaimStance.FOR) }
            repeat(10) { add(ClaimStance.AGAINST) }
            repeat(10) { add(if (Random.nextBoolean()) ClaimStance.FOR else ClaimStance.AGAINST) }
        }.shuffled()

        val updatedAt = Instant.now(clock)

        return FAKE_USER_IDS.zip(randomizedStances).map { (userId, stance) ->
            val userClaim = UserClaimModel(
                claim = claim,
                userId = userId,
                stance = stance,
                priority = null,
                updatedAt = updatedAt
            )
            userClaimRepository.save(userClaim)
            userClaim
        }
    }
}
