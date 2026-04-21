package com.debbly.server.auth.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.OPTIONS
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsConfig::class)
class SecurityConfig(private val corsProperties: CorsConfig) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(OPTIONS, "/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/metrics/**").permitAll()
                    .anyRequest().permitAll()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.bearerTokenResolver(CookieAndHeaderBearerTokenResolver())
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val role = jwt.getClaimAsString("role")
                ?: jwt.getClaimAsString("aud")
                ?: "authenticated"
            listOf(SimpleGrantedAuthority("ROLE_$role"))
        }
        return converter
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.addAllowedOrigin("https://debbly.com")
        config.addAllowedOriginPattern("https://*.debbly.com")
        corsProperties.extraAllowedOriginPatterns.forEach { config.addAllowedOriginPattern(it) }

        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "X-Requested-With")
        config.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}