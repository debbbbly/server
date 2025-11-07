package com.debbly.server.pusher.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "pusher")
data class PusherProperties(
    var appId: String = "",
    var key: String = "",
    var secret: String = "",
    var cluster: String = ""
)