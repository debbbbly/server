package com.debbly.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdServiceTest {
    private val service = IdService()

    @Test
    fun `getId returns 8 character string`() {
        assertEquals(8, service.getId().length)
    }

    @Test
    fun `getId uses only base58 alphabet`() {
        val base58Regex = Regex("^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]+$")
        assertTrue(base58Regex.matches(service.getId()))
    }

    @Test
    fun `getId generates different ids`() {
        val ids = (1..10).map { service.getId() }.toSet()
        assertNotEquals(1, ids.size)
    }
}
