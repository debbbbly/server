package com.debbly.server.event

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.event.model.EventAcceptanceStatus
import com.debbly.server.event.model.EventListFilter
import com.debbly.server.event.model.EventStatus
import com.debbly.server.event.repository.EventCachedRepository
import com.debbly.server.event.repository.EventEntity
import com.debbly.server.event.repository.EventJpaRepository
import com.debbly.server.event.repository.EventParticipantEntity
import com.debbly.server.event.repository.EventParticipantJpaRepository
import com.debbly.server.event.repository.EventReminderEntity
import com.debbly.server.event.repository.EventReminderJpaRepository
import com.debbly.server.home.model.HomeHostResponse
import com.debbly.server.home.model.HomeStageClaimResponse
import com.debbly.server.home.model.HomeStageResponse
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.match.event.MatchFoundEvent
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchOpponentStatus
import com.debbly.server.match.model.MatchReason
import com.debbly.server.match.model.MatchStatus
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.pusher.model.PusherEventName.EVENT_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.settings.SettingsService
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.stage.repository.StageMediaJpaRepository
import com.debbly.server.user.OnlineUsersService
import com.debbly.server.user.repository.UserCachedRepository
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.Clock
import java.time.Instant
import kotlin.jvm.optionals.getOrElse

@Service
class EventService(
    private val eventRepository: EventJpaRepository,
    private val eventCachedRepository: EventCachedRepository,
    private val eventParticipantRepository: EventParticipantJpaRepository,
    private val reminderRepository: EventReminderJpaRepository,
    private val claimRepository: ClaimJpaRepository,
    private val claimCachedRepository: ClaimCachedRepository,
    private val userRepository: UserCachedRepository,
    private val stageJpaRepository: StageJpaRepository,
    private val stageMediaRepository: StageMediaJpaRepository,
    private val matchRepository: MatchRepository,
    private val onlineUsersService: OnlineUsersService,
    private val eventPublisher: ApplicationEventPublisher,
    private val settingsService: SettingsService,
    private val pusherService: PusherService,
    private val idService: IdService,
    private val clock: Clock,
) {

    data class EventUserSummary(
        val userId: String,
        val username: String,
        val avatarUrl: String?
    )

    data class EventClaimSummary(
        val claimId: String,
        val claimSlug: String?,
        val title: String
    )

    data class EventParticipantView(
        val acceptanceId: String,
        val user: EventUserSummary,
        val status: EventAcceptanceStatus,
        val stance: ClaimStance,
        val createdAt: Instant
    )

    data class EventCard(
        val id: String,
        val claim: EventClaimSummary,
        val host: EventUserSummary,
        val hostStance: ClaimStance,
        val startTime: Instant,
        val status: EventStatus,
        val signedUpCount: Long,
        val reminderCount: Long,
        val coverImageUrl: String?
    )

    data class EventListResponse(
        val events: List<EventCard>,
        val nextCursor: String?
    )

    data class EventDetail(
        val id: String,
        val claim: EventClaimSummary,
        val host: EventUserSummary,
        val hostStance: ClaimStance,
        val description: String?,
        val coverImageUrl: String?,
        val startTime: Instant,
        val status: EventStatus,
        val reminderCount: Int,
        val signedUpCount: Int,
        val currentUserState: CurrentUserEventState
    )

    data class CurrentUserEventState(
        val signedUp: Boolean,
        val reminded: Boolean,
        val isHost: Boolean
    )

    data class MatchNextResponse(
        val eventId: String,
        val matchId: String,
        val matchedUser: EventUserSummary,
        val matchedAcceptanceId: String
    )

    data class CreateEventRequest(
        val claimId: String,
        val hostStance: ClaimStance,
        val startTime: Instant,
        val description: String?,
        val coverImageUrl: String?
    )

    data class EventStagesListResponse(
        val stages: List<HomeStageResponse>,
        val nextCursor: String?
    )

    fun listEvents(filter: EventListFilter, limit: Int, cursor: String?): EventListResponse {
        val now = Instant.now(clock)
        val cursorInstant = cursor?.let { parseCursor(it) }

        val events = when (filter) {
            EventListFilter.UPCOMING -> {
                val from = cursorInstant ?: now
                eventRepository.findUpcomingAfter(EventStatus.SCHEDULED, from)
            }

            EventListFilter.LIVE -> {
                val from = cursorInstant ?: Instant.parse("9999-12-31T23:59:59Z")
                eventRepository.findLiveBefore(EventStatus.LIVE, from)
            }

            EventListFilter.PAST -> {
                val from = cursorInstant ?: Instant.parse("9999-12-31T23:59:59Z")
                eventRepository.findPastBefore(listOf(EventStatus.COMPLETED, EventStatus.CANCELLED), from)
            }
        }.take(limit + 1)

        val hasMore = events.size > limit
        val page = events.take(limit)
        val cards = toEventCards(page)

        val nextCursor = if (hasMore) page.lastOrNull()?.startTime?.toString() else null
        return EventListResponse(events = cards, nextCursor = nextCursor)
    }

    fun getEventDetail(eventId: String, userId: String?): EventDetail {
        val event = getEventOrThrow(eventId)
        val claim = claimRepository.findById(event.claimId).getOrElse {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found")
        }

        val owner = userRepository.findById(event.hostUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Host not found")

        val participants = eventParticipantRepository.findByEventIdOrderByCreatedAtAsc(eventId)
        val participantUsers = userRepository.findByIds(participants.map { it.userId })

//        val participantView = participants.mapNotNull { participant ->
//            participantUsers[participant.userId]?.let { user ->
//                EventParticipantView(
//                    acceptanceId = acceptanceKey(participant.eventId, participant.userId),
//                    user = EventUserSummary(
//                        userId = user.userId,
//                        username = user.username,
//                        avatarUrl = user.avatarUrl
//                    ),
//                    stance = participant.stance,
//                    status = participant.status,
//                    createdAt = participant.createdAt
//                )
//            }
//        }

        val meSignedUp = userId?.let { userId ->
            eventParticipantRepository.findByEventIdAndUserId(eventId, userId)
                ?.takeIf { it.status == EventAcceptanceStatus.SIGNED_UP } != null
        } ?: false

        val meReminded = userId?.let { reminderRepository.findByEventIdAndUserId(eventId, it) != null } ?: false

        return EventDetail(
            id = event.eventId,
            claim = EventClaimSummary(
                claimId = claim.claimId,
                claimSlug = claim.slug,
                title = claim.title
            ),
            host = EventUserSummary(owner.userId, owner.username, owner.avatarUrl),
            hostStance = event.hostStance,
            description = event.description,
            coverImageUrl = event.coverImageUrl,
            startTime = event.startTime,
            status = event.status,
            signedUpCount = event.signedUpCount,
            reminderCount = event.reminderCount,
            currentUserState = CurrentUserEventState(
                signedUp = meSignedUp,
                reminded = meReminded,
                isHost = userId == event.hostUserId
            )
        )
    }

    @Transactional
    fun create(hostUserId: String, request: CreateEventRequest): EventDetail {
        claimRepository.findById(request.claimId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found")
        }

        validateCreateRequest(request)

        val now = Instant.now(clock)
        val event = EventEntity(
            eventId = idService.getId(),
            claimId = request.claimId,
            hostUserId = hostUserId,
            hostStance = request.hostStance,
            startTime = request.startTime,
            status = EventStatus.SCHEDULED,
            description = request.description,
            coverImageUrl = request.coverImageUrl,
            createdAt = now,
            updatedAt = now,
            cancelledAt = null
        )

        val saved = eventRepository.save(event)
        return getEventDetail(saved.eventId, hostUserId)
    }

    @Transactional
    fun signUp(eventId: String, userId: String, stance: ClaimStance): EventParticipantView {
        val event = getEventOrThrow(eventId)
        canEventBeJoined(event)

        val existing = eventParticipantRepository.findByEventIdAndUserId(eventId, userId)
            ?.takeIf {  it.status !in setOf(EventAcceptanceStatus.WITHDRAWN, EventAcceptanceStatus.REMOVED) }

        if (existing != null) {
            return toEventParticipantView(existing)
        }

        val now = Instant.now(clock)
        val participant = EventParticipantEntity(
            eventId = eventId,
            userId = userId,
            status = EventAcceptanceStatus.SIGNED_UP,
            stance = stance,
            createdAt = now,
            updatedAt = now
        )

        val saved = try {
            val newParticipant = eventParticipantRepository.save(participant)
            eventRepository.incrementSignedUpCount(eventId)
            newParticipant
        } catch (_: DataIntegrityViolationException) {
            eventParticipantRepository.findByEventIdAndUserId(eventId, userId)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "User already signed up")
        }

        return toEventParticipantView(saved)
    }

    @Transactional
    fun remind(eventId: String, userId: String): Int {
        val event = getEventOrThrow(eventId)
        canEventBeJoined(event)

        val existing = reminderRepository.findByEventIdAndUserId(eventId, userId)
        if (existing != null) {
            return event.reminderCount
        }

        try {
            reminderRepository.save(
                EventReminderEntity(
                    eventId = eventId,
                    userId = userId,
                    createdAt = Instant.now(clock)
                )
            )
        } catch (_: DataIntegrityViolationException) {
            return event.reminderCount
        }

        eventRepository.incrementReminderCount(eventId)
        return event.reminderCount + 1
    }

    @Transactional
    fun cancelSignUp(eventId: String, userId: String) {
        val participant = eventParticipantRepository.findByEventIdAndUserId(eventId, userId)
            ?: return
        if (participant.status != EventAcceptanceStatus.SIGNED_UP) return

        eventParticipantRepository.save(
            participant.copy(
                status = EventAcceptanceStatus.WITHDRAWN,
                updatedAt = Instant.now(clock)
            )
        )
        eventRepository.decrementSignedUpCount(eventId)
        //pushEvent(eventId, PusherMessageType.EVENT_QUEUE_UPDATED, queueSnapshot(eventId))
    }

    @Transactional
    fun cancelRemind(eventId: String, userId: String) {
        reminderRepository.findByEventIdAndUserId(eventId, userId) ?: return
        reminderRepository.deleteByEventIdAndUserId(eventId, userId)
        eventRepository.decrementReminderCount(eventId)
    }

    @Transactional
    fun start(eventId: String, userId: String): EventDetail {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        val now = Instant.now(clock)
        val nextStatus = when (event.status) {
            EventStatus.SCHEDULED -> EventStatus.LIVE
            EventStatus.LIVE -> EventStatus.LIVE
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only scheduled events can be started")
        }

        val updated = eventCachedRepository.save(event.copy(status = nextStatus, updatedAt = now))
        pushEvent(eventId, PusherMessageType.EVENT_STARTED, mapOf("eventId" to eventId, "status" to updated.status))
        return getEventDetail(updated.eventId, userId)
    }

    @Transactional
    fun complete(eventId: String, userId: String): EventDetail {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        val now = Instant.now(clock)
        val nextStatus = when (event.status) {
            EventStatus.LIVE -> EventStatus.COMPLETED
            EventStatus.COMPLETED -> EventStatus.COMPLETED
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only live events can be stopped")
        }

        val updated = eventCachedRepository.save(event.copy(status = nextStatus, updatedAt = now))
        pushEvent(eventId, PusherMessageType.EVENT_STOPPED, mapOf("eventId" to eventId, "status" to updated.status))
        return getEventDetail(updated.eventId, userId)
    }

    @Transactional
    fun cancel(eventId: String, userId: String): EventDetail {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        if (event.status == EventStatus.CANCELLED) {
            return getEventDetail(event.eventId, userId)
        }

        val now = Instant.now(clock)
        val updated = eventCachedRepository.save(
            event.copy(
                status = EventStatus.CANCELLED,
                updatedAt = now,
                cancelledAt = now
            )
        )

        pushEvent(eventId, PusherMessageType.EVENT_CANCELLED, mapOf("eventId" to eventId, "status" to updated.status))
        return getEventDetail(updated.eventId, userId)
    }

    @Transactional
    fun match(eventId: String, userId: String, withUserId: String? = null): MatchNextResponse {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        if (event.status != EventStatus.LIVE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Event must be live")
        }

        // Pick next online signed-up participant (FIFO within online set).
        // Locking the selected row prevents two concurrent match calls from grabbing the same person.
        val participant = if (withUserId.isNullOrBlank()) {
            val signedUp = eventParticipantRepository.findByEventIdAndStatusOrderByCreatedAtAsc(
                eventId, EventAcceptanceStatus.SIGNED_UP
            )
            val onlineUserIds = onlineUsersService.getOnlineUserIds()
            val targetUserId = signedUp.firstOrNull { it.userId in onlineUserIds }?.userId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No online signed-up users available")
            eventParticipantRepository.findSignedUpByEventAndUserForUpdate(eventId, targetUserId)
        } else {
            if (!onlineUsersService.isUserOnline(withUserId)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not online")
            }
            eventParticipantRepository.findSignedUpByEventAndUserForUpdate(eventId, withUserId)
        } ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No signed-up users available")

        val host = userRepository.findById(event.hostUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Host not found")
        val opponent = userRepository.findById(participant.userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val claim = claimRepository.findById(event.claimId).getOrElse {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found")
        }

        // Mark participant immediately so they can't be picked again while the match is pending.
        eventParticipantRepository.save(
            participant.copy(status = EventAcceptanceStatus.MATCHED, updatedAt = Instant.now(clock))
        )
        eventRepository.decrementSignedUpCount(eventId)

        val matchId = idService.getId()
        val match = Match(
            matchId = matchId,
            claim = Match.MatchClaim(claimId = claim.claimId, title = claim.title),
            topicId = claim.topicId,
            eventId = eventId,
            matchReason = MatchReason.CLAIM_MATCH,
            status = MatchStatus.PENDING,
            opponents = listOf(
                Match.MatchOpponent(
                    userId = host.userId,
                    username = host.username,
                    avatarUrl = host.avatarUrl,
                    stance = event.hostStance,
                    status = MatchOpponentStatus.ACCEPTED,
                    ignores = 0,
                ),
                Match.MatchOpponent(
                    userId = opponent.userId,
                    username = opponent.username,
                    avatarUrl = opponent.avatarUrl,
                    stance = participant.stance,
                    status = MatchOpponentStatus.PENDING,
                    ignores = 0,
                ),
            ),
            ttl = settingsService.getMatchTtl(),
            updatedAt = Instant.now(clock),
        )

        matchRepository.save(match)
        eventPublisher.publishEvent(MatchFoundEvent(match))

        val matchedAcceptanceId = acceptanceKey(participant.eventId, participant.userId)
        pushEvent(eventId, PusherMessageType.EVENT_QUEUE_UPDATED, queueSnapshot(eventId))

        return MatchNextResponse(
            eventId = eventId,
            matchId = matchId,
            matchedUser = EventUserSummary(opponent.userId, opponent.username, opponent.avatarUrl),
            matchedAcceptanceId = matchedAcceptanceId,
        )
    }

    @Transactional
    fun removeUser(eventId: String, userId: String, removeUserId: String): EventDetail {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        val participant = eventParticipantRepository.findByEventIdAndUserId(eventId, removeUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User is not in event queue")

        if (participant.status == EventAcceptanceStatus.SIGNED_UP) {
            eventParticipantRepository.save(
                participant.copy(
                    status = EventAcceptanceStatus.REMOVED,
                    updatedAt = Instant.now(clock)
                )
            )
            eventRepository.decrementSignedUpCount(eventId)
        }

        pushEvent(eventId, PusherMessageType.EVENT_QUEUE_UPDATED, queueSnapshot(eventId))
        return getEventDetail(eventId, userId)
    }

    fun getEventStages(eventId: String, limit: Int, cursor: String?): EventStagesListResponse {
        getEventOrThrow(eventId)
        val cursorInstant = cursor?.let {
            runCatching { Instant.parse(it) }.getOrElse {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor")
            }
        }

        val stages = if (cursorInstant != null) {
            stageJpaRepository.findByEventIdBeforeCursor(eventId, cursorInstant)
        } else {
            stageJpaRepository.findByEventId(eventId)
        }.take(limit + 1)
        val hasMore = stages.size > limit
        val page = stages.take(limit)

        val claimIds = page.mapNotNull { it.claimId }.distinct()
        val claimsMap = if (claimIds.isEmpty()) emptyMap() else claimCachedRepository.findByIds(claimIds)
        val userIds = page.flatMap { it.hosts.map { h -> h.id.userId } }.distinct()
        val usersMap = userRepository.findByIds(userIds)
        val mediaMap = stageMediaRepository.findByStageIdIn(page.map { it.stageId }).associateBy { it.stageId }

        val stageResponses = page.mapNotNull { stage ->
            val claim = claimsMap[stage.claimId] ?: return@mapNotNull null
            HomeStageResponse(
                stageId = stage.stageId,
                claim = HomeStageClaimResponse(claim.claimId, claim.slug, claim.categoryId, claim.title),
                hosts = stage.hosts.map { host ->
                    val user = usersMap[host.id.userId]
                    HomeHostResponse(host.id.userId, user?.username ?: "unknown", user?.avatarUrl, host.stance)
                },
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                thumbnailUrl = mediaMap[stage.stageId]?.thumbnailUrl,
                mediaStatus = mediaMap[stage.stageId]?.status
            )
        }

        val nextCursor = if (hasMore) page.lastOrNull()?.createdAt?.toString() else null
        return EventStagesListResponse(stages = stageResponses, nextCursor = nextCursor)
    }

    fun getEventLiveStages(eventId: String): List<HomeStageResponse> {
        getEventOrThrow(eventId)
        val stages = stageJpaRepository.findOpenByEventId(eventId)

        val claimIds = stages.mapNotNull { it.claimId }.distinct()
        val claimsMap = if (claimIds.isEmpty()) emptyMap() else claimCachedRepository.findByIds(claimIds)
        val userIds = stages.flatMap { it.hosts.map { h -> h.id.userId } }.distinct()
        val usersMap = userRepository.findByIds(userIds)
        val mediaMap = stageMediaRepository.findByStageIdIn(stages.map { it.stageId }).associateBy { it.stageId }

        return stages.mapNotNull { stage ->
            val claim = claimsMap[stage.claimId] ?: return@mapNotNull null
            HomeStageResponse(
                stageId = stage.stageId,
                claim = HomeStageClaimResponse(claim.claimId, claim.slug, claim.categoryId, claim.title),
                hosts = stage.hosts.map { host ->
                    val user = usersMap[host.id.userId]
                    HomeHostResponse(host.id.userId, user?.username ?: "unknown", user?.avatarUrl, host.stance)
                },
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                thumbnailUrl = mediaMap[stage.stageId]?.thumbnailUrl,
                mediaStatus = mediaMap[stage.stageId]?.status
            )
        }
    }

    private fun validateCreateRequest(request: CreateEventRequest) {
        val now = Instant.now(clock)
        if (!request.startTime.isAfter(now)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be in the future")
        }

        val descriptionLength = request.description?.trim()?.length ?: 0
        if (descriptionLength > 2000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "description must be <= 2000 characters")
        }

        request.coverImageUrl?.let {
            runCatching { URI.create(it) }.getOrElse {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "coverImageUrl must be a valid URI")
            }
        }
    }

    private fun canEventBeJoined(event: EventEntity) {
        if (event.status == EventStatus.CANCELLED || event.status == EventStatus.COMPLETED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Event is not accepting participants")
        }
    }

    private fun toEventCards(events: List<EventEntity>): List<EventCard> {
        if (events.isEmpty()) return emptyList()

        val claimIds = events.map { it.claimId }.distinct()
        val hostIds = events.map { it.hostUserId }.distinct()

        val claimMap = claimRepository.findByClaimIds(claimIds).associateBy { it.claimId }
        val ownerMap = userRepository.findByIds(hostIds)

        return events.mapNotNull { event ->
            val claim = claimMap[event.claimId] ?: return@mapNotNull null
            val host = ownerMap[event.hostUserId] ?: return@mapNotNull null

            EventCard(
                id = event.eventId,
                claim = EventClaimSummary(
                    claimId = claim.claimId,
                    claimSlug = claim.slug,
                    title = claim.title
                ),
                host = EventUserSummary(host.userId, host.username, host.avatarUrl),
                hostStance = event.hostStance,
                startTime = event.startTime,
                status = event.status,
                signedUpCount = event.signedUpCount.toLong(),
                reminderCount = event.reminderCount.toLong(),
                coverImageUrl = event.coverImageUrl
            )
        }
    }

    private fun toEventParticipantView(participant: EventParticipantEntity): EventParticipantView {
        val user = userRepository.findById(participant.userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        return EventParticipantView(
            acceptanceId = acceptanceKey(participant.eventId, participant.userId),
            user = EventUserSummary(user.userId, user.username, user.avatarUrl),
            status = participant.status,
            stance = participant.stance,
            createdAt = participant.createdAt
        )
    }

    private fun queueSnapshot(eventId: String): Map<String, Any> {
        val queue = eventParticipantRepository.findByEventIdAndStatusOrderByCreatedAtAsc(
            eventId,
            EventAcceptanceStatus.SIGNED_UP
        )
        return mapOf(
            "eventId" to eventId,
            "queue" to queue.map {
                mapOf(
                    "acceptanceId" to acceptanceKey(it.eventId, it.userId),
                    "userId" to it.userId,
                    "createdAt" to it.createdAt
                )
            }
        )
    }

    private fun pushEvent(eventId: String, type: PusherMessageType, payload: Any) {
        pusherService.sendRawChannelMessage("event:$eventId", EVENT_EVENT, message(type, payload))
    }

    private fun parseCursor(cursor: String): Instant {
        return runCatching { Instant.parse(cursor) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor")
        }
    }

    private fun getEventOrThrow(eventId: String): EventEntity {
        return eventCachedRepository.findById(eventId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found")
    }

    private fun enforceHost(event: EventEntity, userId: String) {
        if (event.hostUserId != userId) {
            throw ForbiddenException("Only event host can do this")
        }
    }

    private fun acceptanceKey(eventId: String, userId: String): String = "$eventId:$userId"
}
