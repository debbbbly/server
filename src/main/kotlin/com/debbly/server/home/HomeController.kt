package com.debbly.server.home

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.home.model.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/home")
class HomeController(
    private val homeService: HomeService,
    private val authService: AuthService
) {

    /**
     * Get homepage topics and debate stages.
     * Topics are ordered by rank, stages are ordered by LIVE first, then RECORDED by openedAt DESC.
     */
    @GetMapping("/topics")
    fun getTopics(
        @ExternalUserId externalUserId: String?,
        @RequestParam(required = false) topicCursor: String?,
        @RequestParam(defaultValue = "10") topicLimit: Int,
        @RequestParam(defaultValue = "5") stageLimit: Int
    ): ResponseEntity<HomeTopicsResponse> {
        val userId = externalUserId?.let { authService.authenticateOptional(it)?.userId }
        val response = homeService.getTopics(
            userId = userId,
            topicCursor = topicCursor,
            topicLimit = topicLimit.coerceIn(1, 20),
            stageLimit = stageLimit.coerceIn(1, 20)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Get single topic detail page with paginated stages.
     * Accepts either topicId or topicSlug.
     */
    @GetMapping("/topics/{topicIdOrSlug}")
    fun getTopicDetail(
        @ExternalUserId externalUserId: String?,
        @PathVariable topicIdOrSlug: String,
        @RequestParam(defaultValue = "20") stageLimit: Int
    ): ResponseEntity<HomeTopicResponse> {
        val userId = externalUserId?.let { authService.authenticateOptional(it)?.userId }
        val response = homeService.getTopic(
            userId = userId,
            topicIdOrSlug = topicIdOrSlug,
            stageLimit = stageLimit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Get paginated stages for a topic.
     * Accepts either topicId or topicSlug.
     */
    @GetMapping("/topics/{topicIdOrSlug}/stages")
    fun getTopicStages(
        @PathVariable topicIdOrSlug: String,
        @RequestParam(required = false) stageCursor: String?,
        @RequestParam(defaultValue = "10") stagesLimit: Int
    ): ResponseEntity<TopicStagesResponse> {
        val response = homeService.getTopicStages(
            topicIdOrSlug = topicIdOrSlug,
            stageCursor = stageCursor,
            stagesLimit = stagesLimit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Get most recent stages with cursor pagination, regardless of topic.
     */
    @GetMapping("/stages")
    fun getStages(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<HomeStagesResponse> {
        val response = homeService.getStages(
            cursor = cursor,
            limit = limit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Get top claims with rank-based cursor pagination.
     */
    @GetMapping("/claims")
    fun getClaims(
        @ExternalUserId externalUserId: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<HomeClaimsResponse> {
        val userId = externalUserId?.let { authService.authenticateOptional(it)?.userId }
        val response = homeService.getClaims(
            userId = userId,
            cursor = cursor,
            limit = limit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Get all currently live stages.
     */
    @GetMapping("/live")
    fun getLiveStages(): ResponseEntity<HomeLiveResponse> {
        val response = homeService.getLiveStages()
        return ResponseEntity.ok(response)
    }
}
