package com.debbly.server.event

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.event.model.EventListFilter
import com.debbly.server.home.model.HomeStageResponse
import com.debbly.server.storage.S3Service
import com.debbly.server.viewer.ViewerScope
import com.debbly.server.viewer.ViewerService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/events")
class EventController(
    private val eventService: EventService,
    private val authService: AuthService,
    private val s3Service: S3Service,
    private val viewerService: ViewerService,
) {
    @GetMapping
    fun listEvents(
        @RequestParam(required = false, defaultValue = "upcoming") filter: EventListFilter,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
    ): ResponseEntity<EventService.EventListResponse> {
        val response =
            eventService.listEvents(
                filter = filter,
                limit = limit.coerceIn(1, 100),
                cursor = cursor,
            )

        return ResponseEntity.ok(response)
    }

    @GetMapping("/{eventId}")
    fun getEvent(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<EventService.EventDetail> {
        val userId = externalUserId?.let { authService.authenticateOptional(it)?.userId }
        return ResponseEntity.ok(eventService.getEventDetail(eventId, userId))
    }

    data class CreateBannerUploadUrlRequest(
        val contentType: String,
    )

    data class CreateBannerUploadUrlResponse(
        val key: String,
        val uploadUrl: String,
        val publicUrl: String,
        val expiresInSeconds: Long,
    )

    @PostMapping("/banner/upload-url")
    fun createBannerUploadUrl(
        @ExternalUserId externalUserId: String?,
        @RequestBody request: CreateBannerUploadUrlRequest,
    ): ResponseEntity<CreateBannerUploadUrlResponse> {
        val user = authService.authenticate(externalUserId)
        val upload = s3Service.generateEventBannerUpload(user.userId, request.contentType)
        return ResponseEntity.ok(
            CreateBannerUploadUrlResponse(
                key = upload.key,
                uploadUrl = upload.uploadUrl,
                publicUrl = upload.publicUrl,
                expiresInSeconds = upload.expiresInSeconds,
            ),
        )
    }

    data class CreateEventHttpRequest(
        val claimId: String,
        val hostStance: ClaimStance,
        val startTime: Instant,
        val description: String?,
        val bannerImageKey: String? = null,
    )

    @PostMapping
    fun create(
        @ExternalUserId externalUserId: String?,
        @RequestBody request: CreateEventHttpRequest,
    ): ResponseEntity<EventService.EventDetail> {
        val user = authService.authenticate(externalUserId)
        val bannerImageUrl =
            request.bannerImageKey?.let { key ->
                if (!s3Service.isEventBannerKeyOwnedByUser(user.userId, key)) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid banner image key")
                }
                s3Service.buildUsersPublicUrl(key)
            }

        return ResponseEntity.ok(
            eventService.create(
                user.userId,
                EventService.CreateEventRequest(
                    claimId = request.claimId,
                    hostStance = request.hostStance,
                    startTime = request.startTime,
                    description = request.description,
                    bannerImageUrl = bannerImageUrl,
                ),
            ),
        )
    }

    data class UpdateEventHttpRequest(
        val hostStance: ClaimStance? = null,
        val startTime: Instant? = null,
        val description: String? = null,
        val bannerImageKey: String? = null,
    )

    @PatchMapping("/{eventId}")
    fun update(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: UpdateEventHttpRequest,
    ): ResponseEntity<EventService.EventDetail> {
        val user = authService.authenticate(externalUserId)
        val bannerImageUrl =
            request.bannerImageKey?.let { key ->
                if (!s3Service.isEventBannerKeyOwnedByUser(user.userId, key)) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid banner image key")
                }
                s3Service.buildUsersPublicUrl(key)
            }
        return ResponseEntity.ok(
            eventService.update(
                eventId,
                user.userId,
                EventService.UpdateEventRequest(
                    hostStance = request.hostStance,
                    startTime = request.startTime,
                    description = request.description,
                    bannerImageUrl = bannerImageUrl,
                ),
            ),
        )
    }

    data class SignUpEventRequest(
        val stance: ClaimStance,
    )

    @PostMapping("/{eventId}/signup")
    fun signUp(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: SignUpEventRequest,
    ): ResponseEntity<EventService.EventParticipantView> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(eventService.signUp(eventId, user.userId, request.stance))
    }

    @PostMapping("/{eventId}/remind")
    fun remind(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<Map<String, Any>> {
        val user = authService.authenticate(externalUserId)
        val reminderCount = eventService.remind(eventId, user.userId)
        return ResponseEntity.ok(mapOf("eventId" to eventId, "reminderCount" to reminderCount))
    }

    @DeleteMapping("/{eventId}/signup")
    fun cancelSignUp(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<Void> {
        val user = authService.authenticate(externalUserId)
        eventService.cancelSignUp(eventId, user.userId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{eventId}/remind")
    fun cancelRemind(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<Void> {
        val user = authService.authenticate(externalUserId)
        eventService.cancelRemind(eventId, user.userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{eventId}/start")
    fun start(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<EventService.EventDetail> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(eventService.start(eventId, user.userId))
    }

    @PostMapping("/{eventId}/complete")
    fun stop(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<EventService.EventDetail> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(eventService.complete(eventId, user.userId))
    }

    data class MatchRequest(
        val userId: String,
    )

    @PostMapping("/{eventId}/match")
    fun matchNext(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: MatchRequest?, // nullable, match with user if present
    ): ResponseEntity<EventService.MatchNextResponse> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(eventService.match(eventId, user.userId, request?.userId))
    }

    @DeleteMapping("/{eventId}/participants/{removeUserId}")
    fun removeUser(
        @PathVariable eventId: String,
        @PathVariable removeUserId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<EventService.EventDetail> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(eventService.removeUser(eventId, user.userId, removeUserId))
    }

    @GetMapping("/{eventId}/stages")
    fun listEventStages(
        @PathVariable eventId: String,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<EventService.EventStagesListResponse> =
        ResponseEntity.ok(eventService.getEventStages(eventId, limit.coerceIn(1, 100), cursor))

    @GetMapping("/{eventId}/stages/live")
    fun listLiveEventStages(
        @PathVariable eventId: String,
    ): ResponseEntity<List<HomeStageResponse>> = ResponseEntity.ok(eventService.getEventLiveStages(eventId))

    @PostMapping("/{eventId}/view")
    fun trackViewer(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Unit> {
        val viewerId = externalUserId ?: httpRequest.remoteAddr
        viewerService.trackViewer(ViewerScope.EVENT, eventId, viewerId)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{eventId}/viewers")
    fun getViewerCount(
        @PathVariable eventId: String,
    ): ResponseEntity<ViewerCountResponse> {
        val count = viewerService.getViewerCount(ViewerScope.EVENT, eventId)
        return ResponseEntity.ok(ViewerCountResponse(count))
    }

    data class ViewerCountResponse(
        val count: Long,
    )

    @PostMapping("/{eventId}/cancel")
    fun cancel(
        @PathVariable eventId: String,
        @ExternalUserId externalUserId: String?,
    ): ResponseEntity<EventService.EventDetail> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(eventService.cancel(eventId, user.userId))
    }
}
