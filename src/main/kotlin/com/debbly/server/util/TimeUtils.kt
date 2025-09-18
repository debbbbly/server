package com.debbly.server.util

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

object TimeUtils {
    /**
     * Returns current Instant truncated to milliseconds precision
     * Format: 2025-09-03T19:54:58.753Z (instead of 2025-09-03T19:54:58.753595Z)
     */
    fun nowMillis(clock: Clock): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
    
    /**
     * Truncates any Instant to milliseconds precision
     */
    fun Instant.toMillis(): Instant = this.truncatedTo(ChronoUnit.MILLIS)
}