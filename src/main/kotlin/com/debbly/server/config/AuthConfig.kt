package com.debbly.server.config

import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.validation.annotation.Validated
import javax.crypto.spec.SecretKeySpec

@Validated
@ConfigurationProperties(prefix = "auth")
data class AuthConfigProperties(
    @field:NotBlank val url: String = "",
    @field:NotBlank val publicUrl: String = "",
    @field:NotBlank val jwtSecret: String = "",
    @field:NotBlank val serviceRoleKey: String = ""
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