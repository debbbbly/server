package com.debbly.server.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@ConfigurationProperties(prefix = "supabase")
data class SupabaseConfigProperties(
    val url: String = "",
    val publishableKey: String = "",
    val secretKey: String = "",
    val jwtSecret: String = "",
)

@Configuration
@EnableConfigurationProperties(SupabaseConfigProperties::class)
class SupabaseConfig(private val supabaseConfigProperties: SupabaseConfigProperties) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return try {
            logger.info("Configuring JWT decoder for self-hosted Supabase with JWT secret")

            // Self-hosted Supabase uses symmetric key signing (HS256)
            val secretKey = javax.crypto.spec.SecretKeySpec(
                supabaseConfigProperties.jwtSecret.toByteArray(),
                "HmacSHA256"
            )

            NimbusJwtDecoder
                .withSecretKey(secretKey)
                .build()
                .apply {
                    logger.info("JWT decoder configured successfully for self-hosted Supabase")
                }
        } catch (e: Exception) {
            logger.error("Failed to configure JWT decoder for Supabase", e)
            throw IllegalStateException("Could not configure JWT decoder for Supabase authentication", e)
        }
    }
}