package com.debbly.server.auth.config

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

class CookieAndHeaderBearerTokenResolver : BearerTokenResolver {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val bearerTokenResolver = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? {
        // First, try to resolve from the Authorization header
        val tokenFromHeader = bearerTokenResolver.resolve(request)
        if (tokenFromHeader != null) {
            logger.debug("Found token in Authorization header")
            return tokenFromHeader
        }

        // If not found, try to resolve from the cookie
        val tokenFromCookie = request.cookies?.firstOrNull { it.name == "access_token" || it.name == "accessToken" }?.value
        if (tokenFromCookie != null) {
            logger.debug("Found token in cookie: ${tokenFromCookie.take(50)}...")
        } else {
            logger.debug("No token found in cookies. Available cookies: ${request.cookies?.joinToString { it.name } ?: "none"}")
        }
        return tokenFromCookie
    }
}
