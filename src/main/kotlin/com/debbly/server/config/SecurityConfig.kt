package com.debbly.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers(
                        "/api/v1/user/me",           // Protected: profile
                        "/api/v1/user/settings/**",  // Protected: preferences, etc.
                        "/api/v1/private/**"         // Any other private routes
                    ).authenticated()
                    .anyRequest().permitAll()       // Everything else is public
            }
            .csrf { it.disable() }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> } // this is now a lambda config block
            }

        return http.build()
    }
}