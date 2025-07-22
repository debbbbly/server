package com.debbly.server

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "livekit")
class LiveKitConfig {
    lateinit var url: String
    lateinit var apiKey: String
    lateinit var apiSecret: String
}

