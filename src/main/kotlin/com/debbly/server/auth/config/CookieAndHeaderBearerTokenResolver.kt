package com.debbly.server.auth.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver

class CookieAndHeaderBearerTokenResolver : BearerTokenResolver {

    private val bearerTokenResolver = DefaultBearerTokenResolver()

    override fun resolve(request: HttpServletRequest): String? {
        // First, try to resolve from the Authorization header
        val tokenFromHeader = bearerTokenResolver.resolve(request)
        if (tokenFromHeader != null) {
            return tokenFromHeader
        }

        // If not found, try to resolve from the cookie
        return request.cookies?.firstOrNull { it.name == "access_token" || it.name == "accessToken" }?.value
    }
}
