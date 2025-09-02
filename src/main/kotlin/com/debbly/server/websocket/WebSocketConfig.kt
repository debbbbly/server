package com.debbly.server.websocket

import com.debbly.server.auth.config.CookieAndHeaderBearerTokenResolver
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val matchingWebSocketHandler: MatchingWebSocketHandler,
    private val jwtDecoder: JwtDecoder,
    private val userRepository: UserCachedRepository,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(matchingWebSocketHandler, "/ws/matching")
            .setAllowedOrigins("http://localhost:3000") // Frontend origin
            .setHandshakeHandler(object : DefaultHandshakeHandler() {
                override fun determineUser(request: ServerHttpRequest, wsHandler: WebSocketHandler, attributes: MutableMap<String, Any>): Principal? {
                    val tokenResolver = CookieAndHeaderBearerTokenResolver()
                    if (request is ServletServerHttpRequest) {
                        val token = tokenResolver.resolve(request.servletRequest)
                        if (token != null) {
                            val jwt = jwtDecoder.decode(token)
                            val externalUserId = jwt.subject
                            
                            // Look up platform userId by externalUserId (AWS Cognito ID)
                            val user = userRepository.findByExternalUserId(externalUserId)
                            return user?.let { Principal { it.userId } }
                        }
                    }
                    return null
                }
            })
    }
}