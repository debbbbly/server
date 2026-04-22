package com.debbly.server.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlugServiceTest {
    private val service = SlugService()

    @Test
    fun `slugify converts text to lowercase hyphenated slug`() {
        assertEquals("lukashenko-should-be-free", service.slugify("Lukashenko Should Be Free"))
    }

    @Test
    fun `slugify removes special characters`() {
        assertEquals("hello-world", service.slugify("Hello, World!"))
    }

    @Test
    fun `slugify collapses whitespace`() {
        assertEquals("a-b-c", service.slugify("a   b   c"))
    }

    @Test
    fun `slugify trims trailing hyphens`() {
        assertEquals("abc", service.slugify("abc---"))
    }

    @Test
    fun `slugify normalizes diacritics`() {
        assertEquals("cafe", service.slugify("café"))
    }

    @Test
    fun `slugify handles empty string`() {
        assertEquals("", service.slugify(""))
    }
}
