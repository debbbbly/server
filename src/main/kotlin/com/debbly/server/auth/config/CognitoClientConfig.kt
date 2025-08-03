package com.debbly.server.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient

@Configuration
class CognitoClientConfig {

    @Bean
    fun cognitoIdentityProviderClient(): CognitoIdentityProviderClient {
        return CognitoIdentityProviderClient.builder()
            .region(Region.US_WEST_2) // 👈 replace with your actual region
            .build()
    }
}