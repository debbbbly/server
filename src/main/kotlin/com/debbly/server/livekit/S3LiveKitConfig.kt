package com.debbly.server.livekit

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@ConfigurationProperties(prefix = "s3.lk")
data class S3LiveKitProperties(
    val endpoint: String = "",
    val bucket: BucketConfig = BucketConfig(),
    val region: String = "",
    val accessKey: String = "",
    val secret: String = "",
    val forcePathStyle: Boolean = true,
) {
    data class BucketConfig(
        val egress: String = "",
    )
}

@Configuration
@EnableConfigurationProperties(S3LiveKitProperties::class)
class S3LiveKitConfig(private val properties: S3LiveKitProperties) {

    @Bean("s3LiveKitClient")
    fun s3LiveKitClient(): S3Client {
        val credentials = AwsBasicCredentials.create(
            properties.accessKey,
            properties.secret
        )

        return S3Client.builder()
            .region(Region.of(properties.region))
            .endpointOverride(URI.create(properties.endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .forcePathStyle(properties.forcePathStyle)
            .build()
    }
}
