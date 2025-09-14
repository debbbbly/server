package com.debbly.server.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cognito")
data class CognitoConfig(
    val region: String,
    val clientId: String,
    val userPoolId: String,
) {
    val jwks: String
        get() = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"
}

