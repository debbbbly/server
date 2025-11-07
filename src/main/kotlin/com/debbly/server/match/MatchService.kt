package com.debbly.server.match

import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.match.MatchService.MatchingStatus.*
import com.debbly.server.match.event.MatchAcceptedAllEvent
import com.debbly.server.match.event.MatchAcceptedEvent
import com.debbly.server.match.model.*
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class MatchService(
    private val userClaimRepository: UserClaimCachedRepository,
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserCachedRepository,
    private val categoryRepository: CategoryCachedRepository,
    private val userClaimService: UserClaimService,
    private val matchNotificationService: MatchNotificationService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher,
    private val matchValidationService: MatchValidationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun join(user: UserModel) {
        cancelExistingMatchIfPresent(user.userId)
        matchQueueRepository.save(buildMatchRequest(user))

        logger.debug("User {} joined matchmaking queue", user.userId)
    }

    fun leave(user: UserModel) {
        matchQueueRepository.remove(userId = user.userId)

        logger.debug("User {} left matchmaking queue", user.userId)
    }

    fun skip(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "skip")

        logger.debug(
            "User {} skipped match {} on claim '{}'",
            user.userId, match.matchId, match.claim.title
        )

        matchRepository.delete(match.matchId)
        matchNotificationService.notifyMatchCancelled(match, user.userId, "skip")

        matchQueueRepository.save(buildMatchRequest(user, withSkipClaimIds = setOf(match.claim.claimId)))
        addOpponentsBackToQueue(match, user.userId)
    }

    fun switch(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "switch")

        val userStance = match.opponents
            .firstOrNull() { it.userId == user.userId }
            ?.stance

        logger.info(
            "User {} switched stance to {} on match {} for claim '{}'.",
            user.userId, userStance, match.matchId, match.claim.title
        )

        val withClaimIdToStance = userStance
            ?.let { match.claim.claimId to it }
            ?.also { (_, stance) ->
                userClaimService.updateStance(
                    userId = user.userId,
                    claimId = match.claim.claimId,
                    stance = stance
                )
            }
            ?.let { listOf(it) }
            .orEmpty()

        matchRepository.delete(match.matchId)
        matchNotificationService.notifyMatchCancelled(match, user.userId, "switch")

        matchQueueRepository.save(buildMatchRequest(user, withClaimIdToStance = withClaimIdToStance))
        addOpponentsBackToQueue(match, user.userId)
    }

    fun accept(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "accept")

        if (matchValidationService.userAlreadyAccepted(match, user.userId)) {
            logger.warn("User {} already accepted match {}", user.userId, match.matchId)
            return
        }

        val updatedMatchOpponents = match.opponents
            .map { opponent ->
                if (opponent.userId == user.userId) opponent.copy(status = MatchOpponentStatus.ACCEPTED) else opponent
            }

        val updatedMatchStatus = if (updatedMatchOpponents.all { it.status == MatchOpponentStatus.ACCEPTED })
            MatchStatus.ACCEPTED
        else
            match.status

        val updatedMatch = match.copy(
            opponents = updatedMatchOpponents,
            status = updatedMatchStatus,
            updatedAt = Instant.now(clock)
        )

        matchRepository.save(updatedMatch)
        logger.debug(
            "User {} accepted match {} for claim '{}'",
            user.userId, match.matchId, match.claim.title
        )

        publishMatchAcceptedEvent(updatedMatch, updatedMatchStatus, user.userId)
    }

    private fun cancelExistingMatchIfPresent(userId: String) {
        val existingMatch = matchRepository.findByUserId(userId) ?: return

        logger.debug(
            "User {} has active match {} - cancelling it to join queue",
            userId, existingMatch.matchId
        )
        matchNotificationService.notifyMatchCancelled(existingMatch, userId, "rejoin")
        matchRepository.delete(existingMatch.matchId)

        addOpponentsBackToQueue(existingMatch, userId)
    }

    private fun addOpponentsBackToQueue(match: Match, excludeUserId: String) {
        match.opponents
            .filter { it.userId != excludeUserId }
            .forEach { opponent ->
                matchQueueRepository.save(buildMatchRequest(userRepository.getById(opponent.userId)))
            }
    }

    private fun buildMatchRequest(
        user: UserModel,
        withSkipClaimIds: Collection<String>? = null,
        withClaimIdToStance: Collection<Pair<String, ClaimStance>>? = null,
        ignores: Int = 0
    ): MatchRequest {
        val activeCategoryIds = categoryRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val userClaims = userClaimRepository.findByUserId(user.userId)
            .filter { it.claim.category.categoryId in activeCategoryIds }

        return MatchRequest(
            userId = user.userId,
            claimIdToStance = userClaims.associate { it.claim.claimId to it.stance }
                .plus(withClaimIdToStance.orEmpty()),
            skipClaimIds = withSkipClaimIds.orEmpty(),
            joinedAt = Instant.now(clock),
            ignores = ignores
        )
    }

    private fun publishMatchAcceptedEvent(match: Match, matchStatus: MatchStatus, userId: String) {
        if (matchStatus == MatchStatus.ACCEPTED) {
            eventPublisher.publishEvent(MatchAcceptedAllEvent(match))
        } else {
            eventPublisher.publishEvent(MatchAcceptedEvent(match, userId))
        }
    }

    fun getQueue(): List<MatchRequest> = matchQueueRepository.findAll()

    fun getMatch(userId: String): Match? {
        return matchRepository.findByUserId(userId)
    }

    fun findAllMatches(): List<Match> {
        return matchRepository.findAll()
    }

    fun getMatchingState(user: UserModel): MatchingState =
        matchRepository.findByUserId(user.userId)
            ?.let { match ->
                MatchingState(status = MATCHED, matches = listOf(match))
            }
            ?: let {
                if (matchQueueRepository.find(user.userId) != null) {
                    MatchingState(status = MATCHING)
                } else {
                    MatchingState(status = NOT_MATCHING)
                }
            }

    data class MatchingState(
        val status: MatchingStatus,
        val matches: List<Match> = emptyList()
    )

    enum class MatchingStatus {
        MATCHING, MATCHED, NOT_MATCHING
    }
}
