package com.debbly.server.event

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.event.model.EventAcceptanceStatus
import com.debbly.server.event.model.EventAcceptanceStatus.MATCHED
import com.debbly.server.event.model.EventAcceptanceStatus.NO_SHOW
import com.debbly.server.event.model.EventAcceptanceStatus.REMOVED
import com.debbly.server.event.model.EventAcceptanceStatus.SIGNED_UP
import com.debbly.server.event.model.EventAcceptanceStatus.WITHDRAWN
import com.debbly.server.event.model.EventListFilter
import com.debbly.server.event.model.EventStatus
import com.debbly.server.event.repository.*
import com.debbly.server.home.model.HomeHostResponse
import com.debbly.server.home.model.HomeStageClaimResponse
import com.debbly.server.home.model.HomeStageResponse
import com.debbly.server.home.model.StageRecordingResponse
import com.debbly.server.stage.repository.entities.StageMediaStatus
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.match.MatchingJobService
import com.debbly.server.match.model.ClaimWithStance
import com.debbly.server.match.model.MatchRequest
import com.debbly.server.match.repository.MatchQueueRepository
import com.debbly.server.pusher.model.PusherEventName.EVENT_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.stage.repository.StageMediaJpaRepository
import com.debbly.server.user.OnlineUsersService
import com.debbly.server.user.repository.UserCachedRepository
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
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
    private val matchQueueRepository: MatchQueueRepository,
    private val matchingJobService: MatchingJobService,
    private val onlineUsersService: OnlineUsersService,
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

    data class EventParticipantSummary(
        val userId: String,
        val username: String,
        val online: Boolean
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
        val bannerImageUrl: String?
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
        val bannerImageUrl: String?,
        val startTime: Instant,
        val status: EventStatus,
        val reminderCount: Int,
        val signedUpCount: Int,
        val currentUserState: CurrentUserEventState,
        val participants: List<EventParticipantSummary>,
        val onlineSignedUpCount: Int
    )

    data class CurrentUserEventState(
        val signedUp: Boolean,
        val reminded: Boolean,
        val isHost: Boolean
    )

    data class MatchNextResponse(
        val eventId: String,
    )

    data class CreateEventRequest(
        val claimId: String,
        val hostStance: ClaimStance,
        val startTime: Instant,
        val description: String?,
        val bannerImageUrl: String?
    )

    data class UpdateEventRequest(
        val hostStance: ClaimStance?,
        val startTime: Instant?,
        val description: String?,
        val bannerImageUrl: String?
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
            throw ResponseStatusException(NOT_FOUND, "Claim not found")
        }

        val eventHost = userRepository.findById(event.hostUserId)
            ?: throw ResponseStatusException(NOT_FOUND, "Host not found")

        val participants = eventParticipantRepository.findByEventIdOrderByCreatedAtAsc(eventId)
        val participantUsers = userRepository.findByIds(participants.map { it.userId })

        val allUserIdsForOnlineCheck = (participants.map { it.userId } + event.hostUserId).distinct()
        val onlineMap = onlineUsersService.areUsersOnline(allUserIdsForOnlineCheck)

        val activeParticipants = participants
            .filter { it.status !in setOf(REMOVED, WITHDRAWN) }

        val hostSummary = EventParticipantSummary(
            userId = eventHost.userId,
            username = eventHost.username,
            online = onlineMap[eventHost.userId] == true
        )

        val participantSummaries = listOf(hostSummary) + activeParticipants
            .filter { it.userId != event.hostUserId }
            .sortedWith(
                compareBy(
                { onlineMap[it.userId] != true },
                { it.status != SIGNED_UP },
                { it.createdAt }
            ))
            .mapNotNull { participant ->
                val user = participantUsers[participant.userId] ?: return@mapNotNull null
                EventParticipantSummary(
                    userId = user.userId,
                    username = user.username,
                    online = onlineMap[participant.userId] == true
                )
            }

        val onlineSignedUpCount = participantSummaries.count { p -> p.online }

        val meSignedUp = userId?.let { userId ->
            eventParticipantRepository.findByEventIdAndUserId(eventId, userId)
                ?.takeIf { it.status !in setOf(REMOVED, WITHDRAWN) } != null
        } ?: false

        val meReminded = userId?.let { reminderRepository.findByEventIdAndUserId(eventId, it) != null } ?: false

        return EventDetail(
            id = event.eventId,
            claim = EventClaimSummary(
                claimId = claim.claimId,
                claimSlug = claim.slug,
                title = claim.title
            ),
            host = EventUserSummary(eventHost.userId, eventHost.username, eventHost.avatarUrl),
            hostStance = event.hostStance,
            description = event.description,
            bannerImageUrl = event.bannerImageUrl,
            startTime = event.startTime,
            status = event.status,
            signedUpCount = event.signedUpCount + 1,
            reminderCount = event.reminderCount,
            currentUserState = CurrentUserEventState(
                signedUp = meSignedUp,
                reminded = meReminded,
                isHost = userId == event.hostUserId
            ),
            participants = participantSummaries,
            onlineSignedUpCount = onlineSignedUpCount
        )
    }

    @Transactional
    fun create(hostUserId: String, request: CreateEventRequest): EventDetail {
        claimRepository.findById(request.claimId).orElseThrow {
            ResponseStatusException(NOT_FOUND, "Claim not found")
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
            bannerImageUrl = request.bannerImageUrl,
            createdAt = now,
            updatedAt = now,
            cancelledAt = null
        )

        val saved = eventRepository.save(event)
        return getEventDetail(saved.eventId, hostUserId)
    }

    @Transactional
    fun update(eventId: String, userId: String, request: UpdateEventRequest): EventDetail {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        request.startTime?.let { startTime ->
            val now = Instant.now(clock)
            if (!startTime.isAfter(now)) {
                throw ResponseStatusException(BAD_REQUEST, "startTime must be in the future")
            }
        }

        request.description?.trim()?.let { description ->
            if (description.length > 2000) {
                throw ResponseStatusException(BAD_REQUEST, "description must be <= 2000 characters")
            }
        }

        val updated = eventCachedRepository.save(
            event.copy(
                hostStance = request.hostStance ?: event.hostStance,
                startTime = request.startTime ?: event.startTime,
                description = request.description ?: event.description,
                bannerImageUrl = request.bannerImageUrl ?: event.bannerImageUrl,
                updatedAt = Instant.now(clock)
            )
        )

        return getEventDetail(updated.eventId, userId)
    }

    @Transactional
    fun signUp(eventId: String, userId: String, stance: ClaimStance): EventParticipantView {
        val event = getEventOrThrow(eventId)
        canEventBeJoined(event)

        val existing = eventParticipantRepository.findByEventIdAndUserId(eventId, userId)

        if (existing?.status == REMOVED) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You have been removed from this event")
        }

        if (existing != null && existing.status != WITHDRAWN) {
            return toEventParticipantView(existing)
        }

        val now = Instant.now(clock)
        val participant = EventParticipantEntity(
            eventId = eventId,
            userId = userId,
            status = SIGNED_UP,
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
        if (participant.status != SIGNED_UP) return

        eventParticipantRepository.save(
            participant.copy(
                status = WITHDRAWN,
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
            else -> throw ResponseStatusException(BAD_REQUEST, "Only scheduled events can be started")
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
            else -> throw ResponseStatusException(BAD_REQUEST, "Only live events can be stopped")
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

    fun match(eventId: String, userId: String, withUserId: String? = null): MatchNextResponse {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        if (event.status != EventStatus.LIVE) {
            throw ResponseStatusException(BAD_REQUEST, "Event must be live")
        }

        val now = Instant.now(clock)

        val onlineUserIds = onlineUsersService.getOnlineUserIds()
        if (!withUserId.isNullOrBlank()) {

            if (withUserId !in onlineUserIds) {
                throw ResponseStatusException(BAD_REQUEST, "User is not online")
            }
            val participant = eventParticipantRepository.findByEventIdAndUserId(eventId, withUserId)
                ?.takeIf { it.status !in setOf(REMOVED, WITHDRAWN) }
                ?: throw ResponseStatusException(BAD_REQUEST, "User is not an eligible participant")

            matchQueueRepository.save(
                MatchRequest(
                    userId = event.hostUserId,
                    claims = listOf(ClaimWithStance(event.claimId, event.hostStance)),
                    eventId = eventId,
                    withUserId = withUserId,
                    joinedAt = now,
                )
            )

            matchQueueRepository.save(
                MatchRequest(
                    userId = participant.userId,
                    claims = listOf(ClaimWithStance(event.claimId, participant.stance)),
                    eventId = eventId,
                    withUserId = event.hostUserId,
                    joinedAt = now,
                )
            )

            matchingJobService.runMatching()
            return MatchNextResponse(eventId = eventId)
        }

        val eligible = selectEligibleParticipants(eventId, onlineUserIds)

        // Enqueue host
        matchQueueRepository.save(
            MatchRequest(
                userId = event.hostUserId,
                claims = listOf(ClaimWithStance(event.claimId, event.hostStance)),
                eventId = eventId,
                joinedAt = now,
            )
        )

        // Enqueue eligible participants (preserve FIFO via their signup createdAt)
        eligible.forEach { participant ->
            matchQueueRepository.save(
                MatchRequest(
                    userId = participant.userId,
                    claims = listOf(ClaimWithStance(event.claimId, participant.stance)),
                    eventId = eventId,
                    joinedAt = participant.createdAt,
                )
            )
        }

        return MatchNextResponse(
            eventId = eventId,
        )
    }

    private fun selectEligibleParticipants(
        eventId: String,
        onlineUserIds: Set<String>,
    ): List<EventParticipantEntity> {
        val candidates = eventParticipantRepository
            .findByEventIdAndStatusInOrderByCreatedAtAsc(eventId, listOf(SIGNED_UP, MATCHED, NO_SHOW))
            .filter { it.userId in onlineUserIds }

        return candidates.filter { it.status == SIGNED_UP }.ifEmpty {
            candidates.filter { it.status == MATCHED }.ifEmpty {
                candidates.filter { it.status == NO_SHOW }.ifEmpty {
                    throw ResponseStatusException(BAD_REQUEST, "No online participants available")
                }
            }
        }
    }

    @Transactional
    fun removeUser(eventId: String, userId: String, removeUserId: String): EventDetail {
        val event = getEventOrThrow(eventId)
        enforceHost(event, userId)

        val participant = eventParticipantRepository.findByEventIdAndUserId(eventId, removeUserId)
            ?: throw ResponseStatusException(NOT_FOUND, "User is not in event queue")

        if (participant.status == SIGNED_UP) {
            eventParticipantRepository.save(
                participant.copy(
                    status = REMOVED,
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
                throw ResponseStatusException(BAD_REQUEST, "Invalid cursor")
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
            val media = mediaMap[stage.stageId]
            val liveHlsUrl = if (stage.status == StageStatus.OPEN && media?.status == StageMediaStatus.IN_PROGRESS)
                media.hlsLiveLandscapeUrl else null
            val recording = if (stage.status == StageStatus.CLOSED && stage.isRecorded == true && media != null)
                StageRecordingResponse(media.hlsLandscapeUrl, media.hlsPortraitUrl, media.durationSeconds)
                else null
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
                thumbnailUrl = media?.thumbnailUrl,
                liveHlsUrl = liveHlsUrl,
                recording = recording
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
            val media = mediaMap[stage.stageId]
            val liveHlsUrl = if (stage.status == StageStatus.OPEN && media?.status == StageMediaStatus.IN_PROGRESS)
                media.hlsLiveLandscapeUrl else null
            val recording = if (stage.status == StageStatus.CLOSED && stage.isRecorded == true && media != null)
                StageRecordingResponse(media.hlsLandscapeUrl, media.hlsPortraitUrl, media.durationSeconds)
                else null
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
                thumbnailUrl = media?.thumbnailUrl,
                liveHlsUrl = liveHlsUrl,
                recording = recording
            )
        }
    }

    private fun validateCreateRequest(request: CreateEventRequest) {
        val now = Instant.now(clock)
        if (!request.startTime.isAfter(now)) {
            throw ResponseStatusException(BAD_REQUEST, "startTime must be in the future")
        }

        val descriptionLength = request.description?.trim()?.length ?: 0
        if (descriptionLength > 2000) {
            throw ResponseStatusException(BAD_REQUEST, "description must be <= 2000 characters")
        }

        request.bannerImageUrl?.let {
            runCatching { URI.create(it) }.getOrElse {
                throw ResponseStatusException(BAD_REQUEST, "bannerImageUrl must be a valid URI")
            }
        }
    }

    private fun canEventBeJoined(event: EventEntity) {
        if (event.status == EventStatus.CANCELLED || event.status == EventStatus.COMPLETED) {
            throw ResponseStatusException(BAD_REQUEST, "Event is not accepting participants")
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
                bannerImageUrl = event.bannerImageUrl
            )
        }
    }

    private fun toEventParticipantView(participant: EventParticipantEntity): EventParticipantView {
        val user = userRepository.findById(participant.userId)
            ?: throw ResponseStatusException(NOT_FOUND, "User not found")

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
            SIGNED_UP
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
            throw ResponseStatusException(BAD_REQUEST, "Invalid cursor")
        }
    }

    private fun getEventOrThrow(eventId: String): EventEntity {
        return eventCachedRepository.findById(eventId)
            ?: throw ResponseStatusException(NOT_FOUND, "Event not found")
    }

    private fun enforceHost(event: EventEntity, userId: String) {
        if (event.hostUserId != userId) {
            throw ForbiddenException("Only event host can do this")
        }
    }

    private fun acceptanceKey(eventId: String, userId: String): String = "$eventId:$userId"
}
