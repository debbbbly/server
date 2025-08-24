package com.debbly.server.match

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchJob(
    private val matchService: MatchService
) {

//    ShedLock: library built exactly for this (works with Redis, JDBC, Mongo, etc.).
//    Simple to add, widely used.

    @Scheduled(fixedRate = 10000)
    fun scheduleRunMatching() {
        matchService.runMatching()
    }

    @Scheduled(initialDelay = 5000, fixedRate = 10000)
    fun scheduleRunMatchingConfirmation() {
        matchService.runMatchingConfirmation()
    }
}
