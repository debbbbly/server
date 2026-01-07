package com.debbly.server.claim.top

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TopClaimsUpdateTask(
    private val topClaimsService: TopClaimsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60000)
    fun updateTopClaims() {
        try {
            topClaimsService.calculateAndUpdateTopClaims()
        } catch (e: Exception) {
            logger.error("Error during scheduled top claims update", e)
        }
    }
}