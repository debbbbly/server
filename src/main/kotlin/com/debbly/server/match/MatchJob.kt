package com.debbly.server.match

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchJob(
    private val matchmakingService: MatchmakingService
) {

    @Scheduled(fixedRate = 5000)
    fun scheduleMatching() {
        matchmakingService.runMatching()
    }

}
