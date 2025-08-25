package com.debbly.server

import io.livekit.server.EgressServiceClient
import io.livekit.server.RoomServiceClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "livekit")
class LiveKitConfig {
    lateinit var wss: String
    lateinit var http: String
    lateinit var apiKey: String
    lateinit var apiSecret: String
}

@Configuration
class LiveKitClientConfig(
    private val liveKitConfig: LiveKitConfig
) {
    @Bean
    fun roomServiceClient(): RoomServiceClient {
        return RoomServiceClient.createClient(liveKitConfig.http, liveKitConfig.apiKey, liveKitConfig.apiSecret)
    }

    @Bean
    fun egressServiceClient(): EgressServiceClient {
        return EgressServiceClient.createClient(liveKitConfig.http, liveKitConfig.apiKey, liveKitConfig.apiSecret)
    }
}

