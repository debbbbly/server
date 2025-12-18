package com.debbly.server.stage

import com.debbly.server.livekit.LiveKitService
import com.debbly.server.settings.SettingsService
import com.debbly.server.stage.config.StageProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class EgressCleanupTask(
    private val liveKitService: LiveKitService,
    private val stageProperties: StageProperties,
    private val settingsService: SettingsService,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Check for old active egresses and stop them every 5 minutes
     * Stops egresses older than stage limit + 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    fun cleanupOldEgresses() {
        try {
            if (!settingsService.isCleanupOldEgresses()) {
                return
            }

            logger.debug("Checking for old egresses to cleanup...")

            val now = Instant.now(clock)
            val maxAgeSeconds = (stageProperties.limitMinutes + 5) * 60L
            val cutoffTime = now.minusSeconds(maxAgeSeconds)

            val activeEgresses = liveKitService.listActiveEgresses()
            val oldEgresses = activeEgresses.filter { egress ->
                val startedAt = Instant.ofEpochSecond(egress.startedAt / 1_000_000_000)
                startedAt.isBefore(cutoffTime)
            }

            oldEgresses.forEach { egress ->
                try {
                    val startedAt = Instant.ofEpochSecond(egress.startedAt / 1_000_000_000)
                    val ageMinutes = (now.epochSecond - startedAt.epochSecond) / 60

                    logger.info("Stopping old egress ${egress.egressId} (age: $ageMinutes minutes, room: ${egress.roomName})")
                    liveKitService.stopEgress(egress.egressId)

                } catch (e: Exception) {
                    logger.error("Error stopping egress ${egress.egressId}", e)
                }
            }

        } catch (e: Exception) {
            logger.error("Error during scheduled egress cleanup", e)
        }
    }
}
