package com.debbly.server.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "cognito")
data class CognitoConfig(
    val region: String,
    val clientId: String,
    val userPoolId: String,
//    val domain: String,
) {
    val jwks: String
        get() = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"
}

