package com.debbly.server.match

import com.debbly.server.IdService
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.match.model.MatchStatus
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.settings.SettingsService
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MatchValidationServiceTest {

    private val settings: SettingsService = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:10:00Z"), ZoneOffset.UTC)
    private val eventPublisher: ApplicationEventPublisher = mock()

    private val service = MatchValidationService(
        userClaimRepository = mock<UserClaimCachedRepository>(),
        matchQueueRepository = mock<MatchQueueRepository>(),
        matchRepository = mock<MatchRepository>(),
        userRepository = mock<UserCachedRepository>(),
        idService = mock<IdService>(),
        claimRepository = mock<ClaimCachedRepository>(),
        categoryRepository = mock<CategoryCachedRepository>(),
        userClaimService = mock<UserClaimService>(),
        matchNotificationService = mock<MatchNotificationService>(),
        settings = settings,
        clock = clock,
        eventPublisher = eventPublisher
    )

    private fun match(
        status: MatchStatus = MatchStatus.PENDING,
        opponents: List<Match.MatchOpponent> = listOf(
            Match.MatchOpponent("u1", "alice", null, null, MatchOpponentStatus.PENDING, 0)
        ),
        updatedAt: Instant = Instant.parse("2025-01-01T00:09:30Z")
    ) = Match(
        matchId = "m1",
        claim = Match.MatchClaim("c1", "t"),
        status = status,
        opponents = opponents,
        ttl = 60,
        updatedAt = updatedAt
    )

    @Test
    fun `validateMatchNotFullyAccepted throws when status is ACCEPTED`() {
        assertThrows(IllegalStateException::class.java) {
            service.validateMatchNotFullyAccepted(match(status = MatchStatus.ACCEPTED), "accept")
        }
    }

    @Test
    fun `validateMatchNotExpired throws when updatedAt older than ttl`() {
        whenever(settings.getMatchTtl()).thenReturn(60L)
        val expired = match(updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        assertThrows(IllegalStateException::class.java) {
            service.validateMatchNotExpired(expired, "accept")
        }
    }

    @Test
    fun `validateMatchNotExpired passes when fresh`() {
        whenever(settings.getMatchTtl()).thenReturn(60L)
        service.validateMatchNotExpired(match(), "accept")
    }

    @Test
    fun `validateUserInMatch throws when user not in opponents`() {
        assertThrows(IllegalStateException::class.java) {
            service.validateUserInMatch(match(), "u-missing", "accept")
        }
    }

    @Test
    fun `userAlreadyAccepted returns true when user has accepted`() {
        val m = match(opponents = listOf(
            Match.MatchOpponent("u1", "alice", null, null, MatchOpponentStatus.ACCEPTED, 0)
        ))
        assertTrue(service.userAlreadyAccepted(m, "u1"))
    }

    @Test
    fun `userAlreadyAccepted returns false when user is pending`() {
        assertFalse(service.userAlreadyAccepted(match(), "u1"))
    }
}
