package com.debbly.server.stage

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class StageTimeoutTask(
    private val stageService: StageService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Check for expired stages every minute and close them
     */
     @Scheduled(fixedRate = 5000)
    fun checkExpiredStages() {
        try {
            logger.debug("Checking for expired stages...")
            stageService.closeExpiredStages()
        } catch (e: Exception) {
            logger.error("Error during scheduled stage timeout check", e)
        }
    }
}