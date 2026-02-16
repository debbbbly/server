package com.debbly.server.match

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchingJob {
    private val matchingJobService: MatchingJobService

    constructor(matchingJobService: MatchingJobService) {
        this.matchingJobService = matchingJobService
    }

    @Scheduled(fixedRate = 10000)
    fun scheduleMatching() {
        matchingJobService.runMatching()
    }
}
