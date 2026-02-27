package com.debbly.server.config

import io.livekit.server.EgressServiceClient
import io.livekit.server.RoomServiceClient
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

@Validated
@Component
@ConfigurationProperties(prefix = "livekit")
class LiveKitConfig {
    @field:NotBlank
    lateinit var url: String
    @field:NotBlank
    lateinit var apiKey: String
    @field:NotBlank
    lateinit var apiSecret: String
    var egress: EgressConfig = EgressConfig()

    class EgressConfig {
        var layouts: LayoutsConfig = LayoutsConfig()

        class LayoutsConfig {
            var landscape: String? = null  // 16:9
            var portrait: String? = null   // 9:16
        }
    }
}

enum class EgressLayout {
    LANDSCAPE,  // 16:9 - desktop/web
    PORTRAIT    // 9:16 - mobile
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
