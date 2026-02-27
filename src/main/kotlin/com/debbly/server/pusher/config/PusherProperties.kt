package com.debbly.server.pusher.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@Validated
@Configuration
@ConfigurationProperties(prefix = "pusher")
data class PusherProperties(
    @field:NotBlank var appId: String = "",
    @field:NotBlank var key: String = "",
    @field:NotBlank var secret: String = "",
    @field:NotBlank var cluster: String = ""
)