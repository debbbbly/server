package com.debbly.server.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LivekitConfigTest {

    @Test
    fun `default values`() {
        val config = LivekitConfig.DEFAULT
        assertEquals("h264", config.videoCodec)
        assertEquals(listOf("s720", "s360"), config.simulcastLayers)
        assertEquals(1_500_000, config.maxBitrate)
        assertEquals(30, config.maxFramerate)
        assertTrue(config.dynacast)
        assertTrue(config.adaptiveStream)
    }
}
