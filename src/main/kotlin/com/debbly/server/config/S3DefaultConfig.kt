package com.debbly.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@ConfigurationProperties(prefix = "s3.default")
data class S3DefaultProperties(
    val endpoint: String = "",
    val publicEndpoint: String = "",
    val bucket: BucketConfig = BucketConfig(),
    val region: String = "",
    val accessKey: String = "",
    val secret: String = "",
    val forcePathStyle: Boolean = true,
) {
    data class BucketConfig(
        val users: String = ""
    )
}

@Configuration
@EnableConfigurationProperties(S3DefaultProperties::class)
class S3DefaultConfig(private val properties: S3DefaultProperties) {

    @Bean("s3DefaultClient")
    fun s3DefaultClient(): S3Client {

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
