package com.debbly.server.match

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.opposite
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.claim.user.UserTopicStanceService
import com.debbly.server.home.model.QueueClaimDetail
import com.debbly.server.home.model.QueueResponse
import com.debbly.server.home.model.QueueUserResponse
import com.debbly.server.match.MatchService.MatchingStatus.*
import com.debbly.server.match.event.MatchAcceptedAllEvent
import com.debbly.server.match.event.MatchAcceptedEvent
import com.debbly.server.match.model.*
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.pusher.model.PusherEventName.MATCH_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.MATCH_QUEUE_REMOVED
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class MatchService(
    private val matchQueueRepository: MatchQueueRepository,
    private val matchRepository: MatchRepository,
    private val userClaimService: UserClaimService,
    private val userTopicStanceService: UserTopicStanceService,
    private val matchNotificationService: MatchNotificationService,
    private val clock: Clock,
    private val eventPublisher: ApplicationEventPublisher,
    private val matchValidationService: MatchValidationService,
    private val claimCachedRepository: ClaimCachedRepository,
    private val userCachedRepository: UserCachedRepository,
    private val pusherService: PusherService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun join(user: UserModel, request: JoinMatchRequest) {
        cancelExistingMatchIfPresent(user.userId)
        val existingRequest = matchQueueRepository.find(user.userId)

        // Save stances to DB for each claim
        request.claims?.forEach { claimWithStance ->
            userClaimService.updateStance(
                userId = user.userId,
                claimId = claimWithStance.claimId,
                stance = claimWithStance.stance,
            )
        }

        // Save stances for topics
        request.topics?.forEach { topicWithStance ->
            userTopicStanceService.updateStance(
                userId = user.userId,
                topicId = topicWithStance.topicId,
                stance = topicWithStance.stance,
            )
        }

        val now = Instant.now(clock)
        val matchRequest = MatchRequest(
            userId = user.userId,
            claims = mergeClaims(existingRequest?.claims.orEmpty(), request.claims.orEmpty()),
            topics = mergeTopics(existingRequest?.topics.orEmpty(), request.topics.orEmpty()),
            skipUserIds = existingRequest?.skipUserIds.orEmpty(),
            skipClaimIds = existingRequest?.skipClaimIds.orEmpty(),
            joinedAt = now,
            updatedAt = now,
            ignores = existingRequest?.ignores ?: 0,
            skipCount = existingRequest?.skipCount ?: 0,
            status = QueueStatus.ACTIVE
        )
        matchQueueRepository.save(matchRequest)

        val queueSize = matchQueueRepository.count()
        logger.debug("Queue size after join: {}", queueSize)
    }

    fun leave(user: UserModel, claimIds: List<String>? = null) {
        val existingRequest = matchQueueRepository.find(user.userId)
        if (existingRequest == null) {
            return
        }

        val requestedClaimIds = claimIds.orEmpty().toSet()
        if (requestedClaimIds.isEmpty()) {
            if (existingRequest.topics.isEmpty()) {
                matchQueueRepository.remove(userId = user.userId)
            } else {
                matchQueueRepository.save(
                    existingRequest.copy(
                        claims = emptyList(),
                        skipClaimIds = emptySet()
                    )
                )
            }
        } else {
            val updatedClaims = existingRequest.claims.filterNot { it.claimId in requestedClaimIds }
            if (updatedClaims.isEmpty() && existingRequest.topics.isEmpty()) {
                matchQueueRepository.remove(userId = user.userId)
            } else {
                matchQueueRepository.save(
                    existingRequest.copy(
                        claims = updatedClaims,
                        skipClaimIds = existingRequest.skipClaimIds - requestedClaimIds
                    )
                )
            }
        }

        val queueSize = matchQueueRepository.count()
        logger.debug("Queue size after leave: {}", queueSize)
    }

    /**
     * Skip behavior:
     * - For claim match: Exclude opponent userId from future matches
     * - For topic match: Exclude that claimId from future topic matches
     */
    fun skip(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "skip")

        val existingRequest = matchQueueRepository.find(user.userId)

        matchRepository.delete(match.matchId)
        matchNotificationService.notifyMatchCancelled(match, user.userId, "skip")

        // Get opponent userId
        val opponentUserId = match.opponents
            .firstOrNull { it.userId != user.userId }
            ?.userId

        // Re-queue the user who skipped
        val skipUserIds = if (match.matchReason == MatchReason.CLAIM_MATCH && opponentUserId != null) {
            (existingRequest?.skipUserIds ?: emptySet()) + opponentUserId
        } else {
            existingRequest?.skipUserIds ?: emptySet()
        }

        val skipClaimIds = if (match.matchReason != MatchReason.CLAIM_MATCH) {
            (existingRequest?.skipClaimIds ?: emptySet()) + match.claim.claimId
        } else {
            existingRequest?.skipClaimIds ?: emptySet()
        }

        if (match.matchReason != MatchReason.USER_MATCH) {
            if (existingRequest != null) {
                val newSkipCount = existingRequest.skipCount + 1
                if (newSkipCount >= 2) {
                    // Remove user from queue after 2 skips
                    matchQueueRepository.remove(existingRequest.userId)
                    val data = mapOf("reason" to "skip_limit_reached")
                    pusherService.sendUserNotification(user.userId, MATCH_EVENT, message(MATCH_QUEUE_REMOVED, data))
                    logger.info("User {} removed from queue after {} skips", user.userId, newSkipCount)
                } else {
                    val updatedRequest = existingRequest.copy(
                        skipUserIds = skipUserIds,
                        skipClaimIds = skipClaimIds,
                        updatedAt = Instant.now(clock),
                        skipCount = newSkipCount
                    )
                    matchQueueRepository.save(updatedRequest)
                }
            }

            // Re-queue opponents
            reQueueOpponents(match, user.userId)
        }
    }

    /**
     * Switch behavior:
     * Re-queue with opposite stance on same claim (+ topic if present)
     */
    fun switch(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "switch")

        val existingRequest = matchQueueRepository.find(user.userId)

        val userCurrentStance = match.opponents
            .firstOrNull { it.userId == user.userId }
            ?.stance
        val userWantedStance = userCurrentStance?.opposite()

        matchRepository.delete(match.matchId)
        matchNotificationService.notifyMatchCancelled(match, user.userId, "switch")

        // Update stance in DB
        if (userWantedStance != null) {
            userClaimService.updateStance(
                userId = user.userId,
                claimId = match.claim.claimId,
                stance = userWantedStance,
            )
        }

        // Re-queue with updated stance
        if (existingRequest != null) {
            // Update the claim stance in the request
            val updatedClaims = existingRequest.claims.map { claim ->
                if (claim.claimId == match.claim.claimId && userWantedStance != null) {
                    claim.copy(stance = userWantedStance)
                } else {
                    claim
                }
            }.let { claims ->
                // If claim wasn't in the list, add it
                if (userWantedStance != null && claims.none { it.claimId == match.claim.claimId }) {
                    claims + ClaimWithStance(match.claim.claimId, userWantedStance)
                } else {
                    claims
                }
            }

            val updatedRequest = existingRequest.copy(
                claims = updatedClaims,
                updatedAt = Instant.now(clock)
            )
            matchQueueRepository.save(updatedRequest)
        } else if (userWantedStance != null) {
            // Create new request with the switched stance
            val now = Instant.now(clock)
            val matchRequest = MatchRequest(
                userId = user.userId,
                claims = listOf(ClaimWithStance(match.claim.claimId, userWantedStance)),
                topics = emptyList(),
                skipUserIds = emptySet(),
                skipClaimIds = emptySet(),
                joinedAt = now,
                updatedAt = now,
                ignores = 0
            )
            matchQueueRepository.save(matchRequest)
        }

        reQueueOpponents(match, user.userId)
    }

    fun accept(match: Match, user: UserModel) {
        matchValidationService.validateMatchOperation(match, user.userId, "accept")

        if (matchValidationService.userAlreadyAccepted(match, user.userId)) {
            return
        }

        // Update user stance when they accept the match
        val userStance = match.opponents
            .firstOrNull { it.userId == user.userId }
            ?.stance

        userStance?.let { stance ->
            userClaimService.updateStance(
                userId = user.userId,
                claimId = match.claim.claimId,
                stance = stance,
            )
            logger.debug("Updated stance for user {} on claim {} to {}", user.userId, match.claim.claimId, stance)
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

        publishMatchAcceptedEvent(updatedMatch, updatedMatchStatus, user.userId)
    }

    private fun cancelExistingMatchIfPresent(userId: String) {
        val existingMatch = matchRepository.findByUserId(userId) ?: return

        matchNotificationService.notifyMatchCancelled(existingMatch, userId, "rejoin")
        matchRepository.delete(existingMatch.matchId)

        // Unpause the queue request if it was paused during match
        val existingRequest = matchQueueRepository.find(userId)
        if (existingRequest != null && existingRequest.status == QueueStatus.PAUSED) {
            matchQueueRepository.save(existingRequest.copy(status = QueueStatus.ACTIVE, updatedAt = Instant.now(clock)))
        }
    }

    private fun reQueueOpponents(match: Match, excludeUserId: String) {
        val opponents = match.opponents.filter { it.userId != excludeUserId }

        opponents.forEach { opponent ->
            val existingRequest = matchQueueRepository.find(opponent.userId)
            if (existingRequest != null) {
                // Re-queue with existing request
                val updatedRequest = existingRequest.copy(joinedAt = Instant.now(clock))
                matchQueueRepository.save(updatedRequest)
            } else {
                // Create minimal request for opponent
                val stance = opponent.stance ?: ClaimStance.EITHER
                val matchRequest = MatchRequest(
                    userId = opponent.userId,
                    claims = listOf(ClaimWithStance(match.claim.claimId, stance)),
                    topics = emptyList(),
                    skipUserIds = emptySet(),
                    skipClaimIds = emptySet(),
                    joinedAt = Instant.now(clock),
                    ignores = opponent.ignores
                )
                matchQueueRepository.save(matchRequest)
            }
            logger.debug("Re-queued opponent {} from cancelled match {}", opponent.userId, match.matchId)
        }
    }

    private fun publishMatchAcceptedEvent(match: Match, matchStatus: MatchStatus, userId: String) {
        if (matchStatus == MatchStatus.ACCEPTED) {
            eventPublisher.publishEvent(MatchAcceptedAllEvent(match))
        } else {
            eventPublisher.publishEvent(MatchAcceptedEvent(match, userId))
        }
    }

    fun getQueueDetails(userId: String): QueueResponse {
        val allRequests = matchQueueRepository.findAllActive()
        val myRequests = allRequests.filter { it.userId == userId }
        val otherRequests = allRequests.filter { it.userId != userId }
        val myClaimStances = myRequests
            .flatMap { it.claims }
            .associate { it.claimId to it.stance }

        // Collect all unique claim IDs and user IDs
        val allClaimIds = allRequests.flatMap { r -> r.claims.map { it.claimId } }.distinct()
        val allUserIds = allRequests.map { it.userId }.distinct()

        // Batch fetch claims and users
        val claimsById = allClaimIds.mapNotNull { claimCachedRepository.findById(it) }
            .associateBy { it.claimId }
        val usersById = userCachedRepository.findByIds(allUserIds)

        fun buildClaimDetails(requests: List<MatchRequest>): List<QueueClaimDetail> {
            // Flatten to (claimId, userId, stance) tuples, then group by claimId
            val entries = requests.flatMap { r ->
                r.claims.map { c -> Triple(c.claimId, r.userId, c.stance) }
            }
            return entries.groupBy { it.first }.mapNotNull { (claimId, tuples) ->
                val claim = claimsById[claimId] ?: return@mapNotNull null
                val waitingUsers = tuples.mapNotNull { (_, uId, stance) ->
                    val user = usersById[uId] ?: return@mapNotNull null
                    QueueUserResponse(
                        userId = user.userId,
                        username = user.username,
                        avatarUrl = user.avatarUrl,
                        stance = stance
                    )
                }
                QueueClaimDetail(
                    claimId = claim.claimId,
                    claimSlug = claim.slug,
                    categoryId = claim.categoryId,
                    title = claim.title,
                    forCount = waitingUsers.count { it.stance == ClaimStance.FOR },
                    againstCount = waitingUsers.count { it.stance == ClaimStance.AGAINST },
                    userStance = myClaimStances[claim.claimId],
                    queue = waitingUsers
                )
            }
        }

        return QueueResponse(
            my = buildClaimDetails(myRequests),
            claims = buildClaimDetails(otherRequests)
        )
    }

    fun joinForChallenge(
        hostUser: UserModel,
        acceptorUser: UserModel,
        claimId: String,
        hostStance: ClaimStance,
        acceptorStance: ClaimStance,
        challengeId: String,
    ) {
        cancelExistingMatchIfPresent(hostUser.userId)
        cancelExistingMatchIfPresent(acceptorUser.userId)

        val now = Instant.now(clock)
        val hostRequest = MatchRequest(
            userId = hostUser.userId,
            claims = listOf(ClaimWithStance(claimId, hostStance)),
            withUserId = acceptorUser.userId,
            challengeId = challengeId,
            joinedAt = now,
            updatedAt = now,
        )
        val acceptorRequest = MatchRequest(
            userId = acceptorUser.userId,
            claims = listOf(ClaimWithStance(claimId, acceptorStance)),
            withUserId = hostUser.userId,
            challengeId = challengeId,
            joinedAt = now,
            updatedAt = now,
            autoAccept = true,
        )
        matchQueueRepository.save(hostRequest)
        matchQueueRepository.save(acceptorRequest)

        logger.debug(
            "Challenge {}: queued host {} and acceptor {} for claimId {}",
            challengeId,
            hostUser.userId,
            acceptorUser.userId,
            claimId,
        )
    }

    fun removeFromQueue(userId: String) {
        matchQueueRepository.remove(userId)
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
                val request = matchQueueRepository.find(user.userId)
                when {
                    request == null -> MatchingState(status = NOT_MATCHING)
                    request.status == QueueStatus.PAUSED -> MatchingState(status = IN_MATCH)
                    else -> MatchingState(status = MATCHING)
                }
            }

    data class MatchingState(
        val status: MatchingStatus,
        val matches: List<Match> = emptyList()
    )

    enum class MatchingStatus {
        MATCHING, MATCHED, IN_MATCH, NOT_MATCHING
    }

    private fun mergeClaims(
        existingClaims: List<ClaimWithStance>,
        newClaims: List<ClaimWithStance>,
    ): List<ClaimWithStance> {
        val mergedByClaimId = LinkedHashMap<String, ClaimWithStance>()
        existingClaims.forEach { mergedByClaimId[it.claimId] = it }
        newClaims.forEach { mergedByClaimId[it.claimId] = it }
        return mergedByClaimId.values.toList()
    }

    private fun mergeTopics(
        existingTopics: List<TopicWithStance>,
        newTopics: List<TopicWithStance>,
    ): List<TopicWithStance> {
        val mergedByTopicId = LinkedHashMap<String, TopicWithStance>()
        existingTopics.forEach { mergedByTopicId[it.topicId] = it }
        newTopics.forEach { mergedByTopicId[it.topicId] = it }
        return mergedByTopicId.values.toList()
    }
}
