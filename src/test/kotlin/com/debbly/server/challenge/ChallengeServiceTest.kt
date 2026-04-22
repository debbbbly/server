package com.debbly.server.challenge

import com.debbly.server.IdService
import com.debbly.server.challenge.repository.ChallengeEntity
import com.debbly.server.challenge.repository.ChallengeJpaRepository
import com.debbly.server.challenge.repository.ChallengeStatus
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.StanceToTopic
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.match.MatchService
import com.debbly.server.user.OnlineUsersService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class ChallengeServiceTest {

    private val challengeRepo: ChallengeJpaRepository = mock()
    private val claimRepo: ClaimCachedRepository = mock()
    private val userRepo: UserCachedRepository = mock()
    private val matchService: MatchService = mock()
    private val onlineUsers: OnlineUsersService = mock()
    private val idService: IdService = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

    private val service = ChallengeService(
        challengeRepo, claimRepo, userRepo, matchService, onlineUsers, idService, clock
    )

    private fun user(id: String = "u1") = UserModel(
        userId = id,
        externalUserId = "ext-$id",
        email = "$id@x.y",
        username = id,
        usernameNormalized = id,
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    private fun claim() = ClaimModel(
        claimId = "c1",
        categoryId = "cat",
        title = "t",
        slug = "t",
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        topicId = "topic1",
        stanceToTopic = StanceToTopic.FOR
    )

    private fun challenge(
        hostUserId: String = "u1",
        status: ChallengeStatus = ChallengeStatus.PENDING,
        expiresAt: Instant = Instant.parse("2025-01-02T00:00:00Z")
    ) = ChallengeEntity(
        challengeId = "ch1",
        claimId = "c1",
        hostUserId = hostUserId,
        hostStance = ClaimStance.FOR,
        status = status,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        expiresAt = expiresAt
    )

    @Test
    fun `create rejects EITHER stance`() {
        assertThrows(ResponseStatusException::class.java) {
            service.create(user(), "c1", ClaimStance.EITHER)
        }
    }

    @Test
    fun `create saves pending challenge on happy path`() {
        whenever(claimRepo.getById("c1")).thenReturn(claim())
        whenever(idService.getId()).thenReturn("ch1")
        whenever(challengeRepo.save(any())).thenAnswer { it.arguments[0] as ChallengeEntity }
        whenever(userRepo.getById("u1")).thenReturn(user())

        val result = service.create(user(), "c1", ClaimStance.FOR)

        assertEquals("ch1", result.challengeId)
        assertEquals(ChallengeStatus.PENDING, result.status)
        assertEquals(ClaimStance.FOR, result.hostStance)
    }

    @Test
    fun `cancel throws when caller is not host`() {
        whenever(challengeRepo.findById("ch1")).thenReturn(Optional.of(challenge(hostUserId = "u1")))
        assertThrows(ForbiddenException::class.java) {
            service.cancel(user(id = "other"), "ch1")
        }
    }

    @Test
    fun `cancel throws when challenge not pending`() {
        whenever(challengeRepo.findById("ch1"))
            .thenReturn(Optional.of(challenge(status = ChallengeStatus.ACCEPTED)))
        assertThrows(ResponseStatusException::class.java) {
            service.cancel(user(), "ch1")
        }
    }

    @Test
    fun `cancel succeeds and saves cancelled challenge`() {
        whenever(challengeRepo.findById("ch1")).thenReturn(Optional.of(challenge()))
        whenever(challengeRepo.save(any())).thenAnswer { it.arguments[0] as ChallengeEntity }
        whenever(claimRepo.getById("c1")).thenReturn(claim())
        whenever(userRepo.getById("u1")).thenReturn(user())

        val result = service.cancel(user(), "ch1")

        assertEquals(ChallengeStatus.CANCELLED, result.status)
    }

    @Test
    fun `accept throws when expired`() {
        whenever(challengeRepo.findById("ch1"))
            .thenReturn(Optional.of(challenge(expiresAt = Instant.parse("2024-12-31T00:00:00Z"))))
        assertThrows(ResponseStatusException::class.java) {
            service.accept(user(id = "other"), "ch1")
        }
    }

    @Test
    fun `accept by participant calls joinForChallenge`() {
        whenever(challengeRepo.findById("ch1")).thenReturn(Optional.of(challenge()))
        whenever(userRepo.getById("u1")).thenReturn(user())
        whenever(claimRepo.getById("c1")).thenReturn(claim())

        service.accept(user(id = "u2"), "ch1")

        verify(matchService).joinForChallenge(
            hostUser = user(),
            acceptorUser = user(id = "u2"),
            claimId = "c1",
            hostStance = ClaimStance.FOR,
            acceptorStance = ClaimStance.AGAINST,
            challengeId = "ch1"
        )
    }

    @Test
    fun `markAccepted updates pending to accepted`() {
        whenever(challengeRepo.findById("ch1")).thenReturn(Optional.of(challenge()))
        whenever(challengeRepo.save(any())).thenAnswer { it.arguments[0] as ChallengeEntity }

        service.markAccepted("ch1")

        verify(challengeRepo).save(challenge().copy(status = ChallengeStatus.ACCEPTED))
    }
}
