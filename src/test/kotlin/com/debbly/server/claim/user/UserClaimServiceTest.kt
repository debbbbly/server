package com.debbly.server.claim.user

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.StanceToTopic
import com.debbly.server.claim.model.UserClaimModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserClaimServiceTest {

    private val userClaimRepo: UserClaimCachedRepository = mock()
    private val claimRepo: ClaimCachedRepository = mock()
    private val idService: IdService = mock()
    private val categoryRepo: CategoryCachedRepository = mock()
    private val userRepo: UserCachedRepository = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

    private val service = UserClaimService(userClaimRepo, claimRepo, idService, categoryRepo, userRepo, clock)

    private fun claim() = ClaimModel(
        claimId = "c1",
        categoryId = "cat1",
        title = "t",
        slug = null,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        topicId = "topic1",
        stanceToTopic = StanceToTopic.FOR
    )

    @Test
    fun `getClaims delegates to repo`() {
        whenever(userClaimRepo.findByUserId("u1")).thenReturn(emptyList())
        assertEquals(emptyList<UserClaimModel>(), service.getClaims("u1"))
    }

    @Test
    fun `updateStance deletes when stance null and user-claim exists`() {
        val existing = UserClaimModel(claim(), "u1", ClaimStance.FOR, null, Instant.parse("2025-01-01T00:00:00Z"))
        whenever(userClaimRepo.findByUserIdClaimId("u1", "c1")).thenReturn(existing)

        service.updateStance("u1", "c1", null)

        verify(userClaimRepo).deleteByUserIdAndClaimId("u1", "c1")
    }

    @Test
    fun `updateStance updates stance when user-claim exists`() {
        val existing = UserClaimModel(claim(), "u1", ClaimStance.FOR, null, Instant.parse("2025-01-01T00:00:00Z"))
        whenever(userClaimRepo.findByUserIdClaimId("u1", "c1")).thenReturn(existing)

        service.updateStance("u1", "c1", ClaimStance.AGAINST)

        verify(userClaimRepo).save(existing.copy(stance = ClaimStance.AGAINST))
    }

    @Test
    fun `updateStance creates new user-claim when none exists and claim found`() {
        whenever(userClaimRepo.findByUserIdClaimId("u1", "c1")).thenReturn(null)
        whenever(claimRepo.findById("c1")).thenReturn(claim())

        service.updateStance("u1", "c1", ClaimStance.FOR)

        verify(userClaimRepo).save(
            UserClaimModel(claim(), "u1", ClaimStance.FOR, null, Instant.parse("2025-01-01T00:00:00Z"))
        )
    }

    @Test
    fun `updateStance no-op when null stance and no existing`() {
        whenever(userClaimRepo.findByUserIdClaimId("u1", "c1")).thenReturn(null)

        service.updateStance("u1", "c1", null)

        verify(userClaimRepo, never()).save(org.mockito.kotlin.any())
    }
}
