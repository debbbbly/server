package com.debbly.server.match

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchJob(
    private val matchService: MatchService
) {

//    ShedLock: library built exactly for this (works with Redis, JDBC, Mongo, etc.).
//    Simple to add, widely used.

    // @Scheduled(fixedRate = 5000)
    fun scheduleMatching() {
        matchService.runMatching()
    }

}
