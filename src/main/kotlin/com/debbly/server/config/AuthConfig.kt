package com.debbly.server.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import javax.crypto.spec.SecretKeySpec

@ConfigurationProperties(prefix = "auth")
data class AuthConfigProperties(
    val url: String = "",
    val publicUrl: String = "",
    val jwtSecret: String = "",
    val serviceRoleKey: String = ""
)

@Configuration
@EnableConfigurationProperties(AuthConfigProperties::class)
class AuthConfig(private val authConfigProperties: AuthConfigProperties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return try {

            val secretKey = SecretKeySpec(
                authConfigProperties.jwtSecret.toByteArray(),
                "HmacSHA256"
            )

            NimbusJwtDecoder
                .withSecretKey(secretKey)
                .build()
                .apply {
                    logger.info("JWT decoder configured successfully with HS256 algorithm")
                }
        } catch (e: Exception) {
            throw IllegalStateException("Could not configure JWT decoder for GoTrue authentication", e)
        }
    }
}