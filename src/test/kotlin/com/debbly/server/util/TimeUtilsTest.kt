package com.debbly.server.util

import com.debbly.server.util.TimeUtils.toMillis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TimeUtilsTest {

    @Test
    fun `nowMillis returns instant truncated to milliseconds`() {
        val fixedInstant = Instant.parse("2025-09-03T19:54:58.753595Z")
        val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

        val result = TimeUtils.nowMillis(clock)

        assertEquals(Instant.parse("2025-09-03T19:54:58.753Z"), result)
    }

    @Test
    fun `toMillis truncates nanos`() {
        val instant = Instant.parse("2025-01-01T00:00:00.123456789Z")
        assertEquals(Instant.parse("2025-01-01T00:00:00.123Z"), instant.toMillis())
    }
}
