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

@ConfigurationProperties(prefix = "s3")
data class S3ConfigProperties(
    val endpoint: String = "",
    val bucket: BucketConfig = BucketConfig(),
    val region: String = "",
    val accessKey: String = "",
    val secret: String = "",
    val forcePathStyle: Boolean = true,
) {
    data class BucketConfig(
        val egress: String = "",
        val avatars: String = ""
    )
}

@Configuration
@EnableConfigurationProperties(S3ConfigProperties::class)
class S3Config(private val s3ConfigProperties: S3ConfigProperties) {

    @Bean
    fun s3Client(): S3Client {
        val credentials = AwsBasicCredentials.create(
            s3ConfigProperties.accessKey,
            s3ConfigProperties.secret
        )

        return S3Client.builder()
            .region(Region.of(s3ConfigProperties.region))
            .endpointOverride(URI.create(s3ConfigProperties.endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .forcePathStyle(s3ConfigProperties.forcePathStyle)
            .build()
    }
}
