package com.debbly.server.home

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.home.model.HomeTopicResponse
import com.debbly.server.home.model.HomeTopicsResponse
import com.debbly.server.home.model.TopicStagesResponse
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
     */
    @GetMapping("/topics/{topicId}")
    fun getTopicDetail(
        @ExternalUserId externalUserId: String?,
        @PathVariable topicId: String,
        @RequestParam(defaultValue = "20") stageLimit: Int
    ): ResponseEntity<HomeTopicResponse> {
        val userId = externalUserId?.let { authService.authenticateOptional(it)?.userId }
        val response = homeService.getTopic(
            userId = userId,
            topicId = topicId,
            stageLimit = stageLimit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(response)
    }

    /**
     * Get paginated stages for a topic.
     */
    @GetMapping("/topics/{topicId}/stages")
    fun getTopicStages(
        @PathVariable topicId: String,
        @RequestParam(required = false) stageCursor: String?,
        @RequestParam(defaultValue = "10") stagesLimit: Int
    ): ResponseEntity<TopicStagesResponse> {
        val response = homeService.getTopicStages(
            topicId = topicId,
            stageCursor = stageCursor,
            stagesLimit = stagesLimit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(response)
    }
}
