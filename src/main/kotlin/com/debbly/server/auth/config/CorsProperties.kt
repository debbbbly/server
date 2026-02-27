package com.debbly.server.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val extraAllowedOriginPatterns: List<String> = emptyList()
)
