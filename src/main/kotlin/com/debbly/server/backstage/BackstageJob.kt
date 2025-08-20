package com.debbly.server.backstage

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BackstageJob(
    private val backstageService: BackstageService
) {

//    ShedLock: library built exactly for this (works with Redis, JDBC, Mongo, etc.).
//    Simple to add, widely used.

    @Scheduled(fixedRate = 10000)
    fun scheduleRunMatching() {
        backstageService.runMatching()
    }

    @Scheduled(initialDelay = 5000, fixedRate = 10000)
    fun scheduleRunMatchingConfirmation() {
        backstageService.runMatchingConfirmation()
    }
}
