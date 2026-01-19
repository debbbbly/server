package com.debbly.server.claim.topic

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/topics")
class TopicController(
    private val topicService: TopicService
) {
    @PostMapping("/similarity")
    fun calculateSimilarity(
        @RequestBody request: CalculateSimilarityRequest
    ): ResponseEntity<TopicSimilarityResult> {
        val result = topicService.calculateAndStoreSimilarity(request.topicId1, request.topicId2)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(result)
    }

    data class CalculateSimilarityRequest(
        val topicId1: String,
        val topicId2: String
    )

}
