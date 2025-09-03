package com.debbly.server.stage.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "stage")
data class StageProperties(
    var limitMinutes: Int = 5
)