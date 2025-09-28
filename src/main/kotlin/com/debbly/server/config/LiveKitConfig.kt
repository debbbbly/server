package com.debbly.server.config

import io.livekit.server.EgressServiceClient
import io.livekit.server.RoomServiceClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "livekit")
class LiveKitConfig {
    lateinit var url: String
    lateinit var apiKey: String
    lateinit var apiSecret: String
}

@Configuration
class LiveKitClientConfig(
    private val liveKitConfig: LiveKitConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun roomServiceClient(): RoomServiceClient {
        return RoomServiceClient.createClient(liveKitConfig.url, liveKitConfig.apiKey, liveKitConfig.apiSecret)
    }

    @Bean
    fun egressServiceClient(): EgressServiceClient {
        return EgressServiceClient.createClient(liveKitConfig.url, liveKitConfig.apiKey, liveKitConfig.apiSecret)
    }
}
