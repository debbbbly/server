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
        val matchRequest = buildMatchRequest(user)
        matchQueueRepository.save(matchRequest)

        logger.info(
            "User {} ({}) joined queue with {} claims, {} skipped",
            user.userId, user.username, matchRequest.claimIdToStance.size, matchRequest.skipClaimIds.size
        )
        logger.debug("  -> Claims: {}", matchRequest.claimIdToStance)
        logger.debug("  -> Skipped: {}", matchRequest.skipClaimIds)

        val queueSize = matchQueueRepository.count()
        logger.info("Queue size after join: {}", queueSize)
    }

    fun leave(user: UserModel) {
        val existingRequest = matchQueueRepository.find(user.userId)
        matchQueueRepository.remove(userId = user.userId)

        if (existingRequest != null) {
            logger.info("User {} ({}) left matchmaking queue", user.userId, user.username)
            val queueSize = matchQueueRepository.count()
            logger.info("Queue size after leave: {}", queueSize)
        } else {
            logger.warn("User {} ({}) attempted to leave queue but was not in queue", user.userId, user.username)
        }
    }

    fun skip(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "skip")

        logger.info(
            "User {} ({}) skipped match {} on claim '{}', adding claim to skip list",
            user.userId, user.username, match.matchId, match.claim.title
        )

        matchRepository.delete(match.matchId)
        matchNotificationService.notifyMatchCancelled(match, user.userId, "skip")

        val matchRequest = buildMatchRequest(user, withSkipClaimIds = setOf(match.claim.claimId))
        matchQueueRepository.save(matchRequest)
        logger.info("User {} re-queued with {} skipped claims", user.userId, matchRequest.skipClaimIds.size)

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

        logger.info(
            "User {} has active match {} (claim: '{}') - cancelling to join queue",
            userId, existingMatch.matchId, existingMatch.claim.title
        )
        matchNotificationService.notifyMatchCancelled(existingMatch, userId, "rejoin")
        matchRepository.delete(existingMatch.matchId)

        addOpponentsBackToQueue(existingMatch, userId)
    }

    private fun addOpponentsBackToQueue(match: Match, excludeUserId: String) {
        val opponents = match.opponents.filter { it.userId != excludeUserId }

        if (opponents.isEmpty()) {
            logger.debug("No opponents to re-queue for match {}", match.matchId)
            return
        }

        logger.info(
            "Re-queuing {} opponent(s) from match {} (excludes user {})",
            opponents.size, match.matchId, excludeUserId
        )

        opponents.forEach { opponent ->
            val user = userRepository.getById(opponent.userId)
            val matchRequest = buildMatchRequest(user)
            matchQueueRepository.save(matchRequest)
            logger.info(
                "  -> Re-queued opponent {} ({}) with {} claims",
                opponent.userId, opponent.username, matchRequest.claimIdToStance.size
            )
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
