package com.debbly.server.claim.top

import com.debbly.server.claim.topic.top.TopTopicsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TopClaimsUpdateTask(
    private val topClaimsService: TopClaimsService,
    private val topTopicService: TopTopicsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 120000)
    fun updateTopClaims() {
        try {
            topClaimsService.calculateAndUpdateTopClaims()
            topTopicService.calculateAndUpdateTopTopics()
        } catch (e: Exception) {
            logger.error("Error during scheduled top claims update", e)
        }
    }
}