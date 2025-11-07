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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class MatchValidationService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimCachedRepository,
    private val categoryRepository: CategoryCachedRepository,
    private val userClaimService: UserClaimService,
    private val matchNotificationService: MatchNotificationService,
    private val settings: SettingsService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun validateMatchOperation(match: Match, userId: String, operation: String) {
        validateMatchNotFullyAccepted(match, operation)
        validateMatchNotExpired(match, operation)
        validateUserInMatch(match, userId, operation)
    }

    fun validateMatchNotFullyAccepted(match: Match, operation: String) {
        if (match.status == MatchStatus.ACCEPTED) {
            throw IllegalStateException("Cannot $operation: match ${match.matchId} is already fully accepted")
        }
    }

    fun validateMatchNotExpired(match: Match, operation: String) {
        val now = Instant.now(clock)
        val expirationThreshold = now.minusSeconds(settings.getMatchTtl())
        if (match.updatedAt.isBefore(expirationThreshold)) {
            throw IllegalStateException("Cannot $operation: match ${match.matchId} has expired")
        }
    }

    fun validateUserInMatch(match: Match, userId: String, operation: String) {
        val userInMatch = match.opponents.any { it.userId == userId }
        if (!userInMatch) {
            throw IllegalStateException("Cannot $operation: user $userId is not part of match ${match.matchId}")
        }
    }

    fun userAlreadyAccepted(match: Match, userId: String): Boolean {
        return match.opponents
            .firstOrNull { it.userId == userId }
            ?.status == MatchOpponentStatus.ACCEPTED
    }
}