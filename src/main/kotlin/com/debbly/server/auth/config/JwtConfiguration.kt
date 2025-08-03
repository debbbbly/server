package com.debbly.server.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
class JwtConfiguration(private val cognitoConfig: CognitoConfig) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withJwkSetUri(cognitoConfig.jwks).build()
    }
}