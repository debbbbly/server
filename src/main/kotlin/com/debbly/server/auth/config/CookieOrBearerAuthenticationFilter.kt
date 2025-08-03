package com.debbly.server.auth.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

class CookieOrBearerAuthenticationFilter(
    private val jwtDecoder: JwtDecoder
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)

        if (token != null) {
            try {
                val jwt = jwtDecoder.decode(token)
                val authentication = JwtAuthenticationToken(jwt)
                SecurityContextHolder.getContext().authentication = authentication
            } catch (ex: JwtException) {
                 logger.warn("Invalid JWT: ${ex.message}")
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        request.getHeader("Authorization")?.let {
            if (it.startsWith("Bearer ")) return it.removePrefix("Bearer ").trim()
        }

        return request.cookies?.firstOrNull { it.name == "access_token" || it.name == "accessToken" }?.value
    }
}