package com.debbly.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "s3")
data class S3ConfigProperties(
    val endpoint: String = "",
    val bucket: String = "",
    val region: String = "",
    val accessKey: String = "",
    val secret: String = "",
    val forcePathStyle: Boolean = true,
)

@Configuration
@EnableConfigurationProperties(S3ConfigProperties::class)
class S3Config
