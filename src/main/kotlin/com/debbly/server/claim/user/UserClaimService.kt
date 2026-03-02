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
        private const val MIN_FAKE_STANCES = 10
        private const val MAX_FAKE_STANCES = 20
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
        val availableFakeUserIds = FAKE_USER_IDS.filter(fakeUsersById::containsKey)
        if (availableFakeUserIds.size < MIN_FAKE_STANCES) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Need at least $MIN_FAKE_STANCES fake users, found ${availableFakeUserIds.size}"
            )
        }

        val updatedAt = Instant.now(clock)
        val fakeStanceCount = Random.nextInt(
            from = MIN_FAKE_STANCES,
            until = minOf(MAX_FAKE_STANCES, availableFakeUserIds.size) + 1
        )
        val selectedFakeUserIds = availableFakeUserIds.shuffled().take(fakeStanceCount)
        val randomizedStances = buildRandomizedStances(fakeStanceCount)

        availableFakeUserIds.forEach { userId ->
            userClaimRepository.deleteByUserIdAndClaimId(userId, claimId)
        }

        return selectedFakeUserIds.zip(randomizedStances).map { (userId, stance) ->
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

    private fun buildRandomizedStances(count: Int): List<ClaimStance> {
        val baseCount = count / 3
        val randomCount = count - (baseCount * 2)

        return buildList {
            repeat(baseCount) { add(ClaimStance.FOR) }
            repeat(baseCount) { add(ClaimStance.AGAINST) }
            repeat(randomCount) {
                add(if (Random.nextBoolean()) ClaimStance.FOR else ClaimStance.AGAINST)
            }
        }.shuffled()
    }
}
