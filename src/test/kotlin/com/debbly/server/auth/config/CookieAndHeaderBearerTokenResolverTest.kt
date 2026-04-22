package com.debbly.server.auth.config

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CookieAndHeaderBearerTokenResolverTest {

    private val resolver = CookieAndHeaderBearerTokenResolver()

    @Test
    fun `resolves token from Authorization header`() {
        val request: HttpServletRequest = mock()
        whenever(request.getHeader("Authorization")).thenReturn("Bearer header-token")

        assertEquals("header-token", resolver.resolve(request))
    }

    @Test
    fun `falls back to access_token cookie`() {
        val request: HttpServletRequest = mock()
        whenever(request.cookies).thenReturn(arrayOf(Cookie("access_token", "cookie-token")))

        assertEquals("cookie-token", resolver.resolve(request))
    }

    @Test
    fun `falls back to accessToken cookie`() {
        val request: HttpServletRequest = mock()
        whenever(request.cookies).thenReturn(arrayOf(Cookie("accessToken", "camel-token")))

        assertEquals("camel-token", resolver.resolve(request))
    }

    @Test
    fun `returns null when no header or cookie`() {
        val request: HttpServletRequest = mock()
        whenever(request.cookies).thenReturn(null)

        assertNull(resolver.resolve(request))
    }
}
