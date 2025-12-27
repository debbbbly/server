package com.debbly.server.auth.service

import com.debbly.server.config.AuthConfigProperties
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.user.repository.UserCachedRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.GET
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class AuthService(
    private val authConfig: AuthConfigProperties,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val userCachedRepository: UserCachedRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun authenticate(externalUserId: String?) =
        externalUserId?.let {
            userCachedRepository.findByExternalUserId(externalUserId) ?: throw UnauthorizedException()
        } ?: throw UnauthorizedException()

    fun signIn(email: String, password: String): SupabaseAuthResponse {
        val url = "${authConfig.url}/token?grant_type=password"

        val request = mapOf("email" to email, "password" to password)

        try {
            val entity = HttpEntity(request, defaultHeaders())
            val response = restTemplate.postForEntity(url, entity, SupabaseAuthResponse::class.java)

            return response.body ?: SupabaseAuthResponse(error = "Empty response from auth server")
        } catch (e: HttpClientErrorException.BadRequest) {
            return SupabaseAuthResponse(success = false, error = "Invalid credentials")
        } catch (e: Exception) {
            throw RuntimeException("Authentication failed during sign in.", e)
        }
    }

    fun signUp(email: String, password: String): SupabaseAuthResponse {
        val validationError = validatePassword(password)
        if (validationError != null) {
            return SupabaseAuthResponse(success = false, error = validationError)
        }

        val url = "${authConfig.url}/signup"
        val request = mapOf(
            "email" to email,
            "password" to password
        )

        try {
            val entity = HttpEntity(request, defaultHeaders())
            val response = restTemplate.postForEntity(url, entity, SupabaseUserResponse::class.java)
            val userResponse = response.body ?: return SupabaseAuthResponse(error = "Empty response from auth server")

            return SupabaseAuthResponse(
                user = userResponse.toSupabaseUser(),
                success = true
            )
        } catch (e: HttpClientErrorException) {
            return SupabaseAuthResponse(error = e.message ?: "Signup failed")
        } catch (e: Exception) {
            throw RuntimeException("Authentication failed during sign up.", e)
        }
    }

    fun getUserById(userId: String): SupabaseUserResponse? {
        val url = "${authConfig.url}/admin/users/$userId"

        return try {
            val entity = HttpEntity<String>(null, adminHeaders())
            val response = restTemplate.exchange(url, GET, entity, SupabaseUserResponse::class.java)
            response.body
        } catch (e: HttpClientErrorException.NotFound) {
            null
        } catch (e: Exception) {
            throw RuntimeException("Authentication failed.", e)
        }
    }

    fun refreshToken(refreshToken: String): SupabaseAuthResponse {
        val url = "${authConfig.url}/token?grant_type=refresh_token"
        val headers = defaultHeaders()

        val request = mapOf(
            "refresh_token" to refreshToken
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                parseAuthResponse(response.body as Map<String, Any>)
            } else {
                SupabaseAuthResponse(error = "Token refresh failed")
            }
        } catch (e: RestClientException) {
            logger.error("Token refresh failed", e)
            SupabaseAuthResponse(error = e.message ?: "Token refresh failed")
        }
    }

    fun signOut(accessToken: String): SupabaseAuthResponse {
        val url = "${authConfig.url}/logout"
        val headers = defaultHeaders()
        headers.setBearerAuth(accessToken)

        return try {
            val entity = HttpEntity<String>(null, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                SupabaseAuthResponse(success = true)
            } else {
                SupabaseAuthResponse(error = "Signout failed")
            }
        } catch (e: RestClientException) {
            logger.error("Signout failed", e)
            SupabaseAuthResponse(error = e.message ?: "Signout failed")
        }
    }

    fun resetPassword(email: String): SupabaseAuthResponse {
        val url = "${authConfig.url}/recover"
        val headers = defaultHeaders()

        val request = mapOf(
            "email" to email
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                SupabaseAuthResponse(success = true)
            } else {
                SupabaseAuthResponse(error = "Password reset failed")
            }
        } catch (e: RestClientException) {
            logger.error("Password reset failed", e)
            SupabaseAuthResponse(error = e.message ?: "Password reset failed")
        }
    }

    fun confirmSignUp(token: String, type: String = "signup"): SupabaseAuthResponse {
        val url = "${authConfig.url}/verify"
        val headers = defaultHeaders()

        val request = mapOf(
            "token" to token,
            "type" to type
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                parseAuthResponse(response.body as Map<String, Any>)
            } else {
                SupabaseAuthResponse(error = "Email confirmation failed")
            }
        } catch (e: RestClientException) {
            logger.error("Email confirmation failed", e)
            SupabaseAuthResponse(error = e.message ?: "Email confirmation failed")
        }
    }

    fun resendConfirmation(email: String): SupabaseAuthResponse {
        val url = "${authConfig.url}/resend"
        val headers = defaultHeaders()

        val request = mapOf(
            "email" to email,
            "type" to "signup"
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                SupabaseAuthResponse(success = true)
            } else {
                SupabaseAuthResponse(error = "Resend confirmation failed")
            }
        } catch (e: RestClientException) {
            logger.error("Resend confirmation failed", e)
            SupabaseAuthResponse(error = e.message ?: "Resend confirmation failed")
        }
    }

    fun validateToken(accessToken: String): SupabaseUser? {
        val url = "${authConfig.url}/user"
        val headers = defaultHeaders()
        headers.setBearerAuth(accessToken)

        return try {
            val entity = HttpEntity<String>(null, headers)
            val response = restTemplate.exchange(url, GET, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                val userData = response.body as? Map<String, Any>
                userData?.let { parseUser(it) }
            } else {
                logger.warn("Token validation failed with status: ${response.statusCode}")
                null
            }
        } catch (e: RestClientException) {
            logger.error("Token validation failed", e)
            null
        }
    }

    fun updateUserMetadata(accessToken: String, metadata: Map<String, Any>): Boolean {
        val url = "${authConfig.url}/user"
        val headers = defaultHeaders()
        headers.setBearerAuth(accessToken)

        val request = mapOf(
            "data" to metadata
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.PUT,
                entity,
                Map::class.java
            )

            response.statusCode.is2xxSuccessful
        } catch (e: RestClientException) {
            logger.error("Failed to update user metadata", e)
            false
        }
    }

    fun updatePassword(accessToken: String, newPassword: String): Boolean {
        val validationError = validatePassword(newPassword)
        if (validationError != null) {
            logger.error("Password validation failed: $validationError")
            return false
        }

        val url = "${authConfig.url}/user"
        val headers = defaultHeaders()
        headers.setBearerAuth(accessToken)

        val request = mapOf(
            "password" to newPassword
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.PUT,
                entity,
                Map::class.java
            )

            response.statusCode.is2xxSuccessful
        } catch (e: RestClientException) {
            logger.error("Failed to update password", e)
            false
        }
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        this["Authorization"] = "Bearer ${authConfig.serviceRoleKey}"
    }

    private fun defaultHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    private fun parseAuthResponse(responseBody: Map<String, Any>): SupabaseAuthResponse {
        val accessToken = responseBody["access_token"] as? String
        val refreshToken = responseBody["refresh_token"] as? String
        val user = responseBody["user"] as? Map<String, Any>

        return SupabaseAuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user?.let { parseUser(it) },
            success = accessToken != null
        )
    }

    fun initiateGoogleOAuth(redirectUrl: String? = null): SupabaseOAuthResponse {
        // Use publicUrl for browser-accessible OAuth redirect
        val publicUrl = authConfig.publicUrl.ifEmpty { authConfig.url }
        val baseUrl = "$publicUrl/authorize"
        val params = mutableMapOf(
            "provider" to "google"
        )

        redirectUrl?.let { params["redirect_to"] = it }

        val queryString =
            params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        val authUrl = "$baseUrl?$queryString"

        return SupabaseOAuthResponse(
            authUrl = authUrl,
            success = true
        )
    }


    private fun parseUser(userData: Map<String, Any>): SupabaseUser {
        return SupabaseUser(
            id = userData["id"] as String,
            email = userData["email"] as? String
        )
    }

    private fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters long"
        }

        val hasLetter = password.any { it.isLetter() }
        if (!hasLetter) {
            return "Password must contain at least one letter"
        }

        val hasDigit = password.any { it.isDigit() }
        if (!hasDigit) {
            return "Password must contain at least one digit"
        }

        return null
    }
}

data class SupabaseAuthResponse(
    @com.fasterxml.jackson.annotation.JsonProperty("access_token")
    val accessToken: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("refresh_token")
    val refreshToken: String? = null,

    val user: SupabaseUser? = null,
    val success: Boolean = false,
    val error: String? = null
)

data class SupabaseUser(
    val id: String,
    val email: String?
)

data class SupabaseUserResponse(
    val id: String,
    val email: String? = null,
    val aud: String? = null,
    val role: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("email_confirmed_at")
    val emailConfirmedAt: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("confirmation_sent_at")
    val confirmationSentAt: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("confirmed_at")
    val confirmedAt: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("created_at")
    val createdAt: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("updated_at")
    val updatedAt: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("last_sign_in_at")
    val lastSignInAt: String? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("app_metadata")
    val appMetadata: Map<String, Any>? = null,

    @com.fasterxml.jackson.annotation.JsonProperty("user_metadata")
    val userMetadata: Map<String, Any>? = null
) {
    fun toSupabaseUser() = SupabaseUser(id = id, email = email)
}

data class SupabaseOAuthResponse(
    val authUrl: String? = null,
    val success: Boolean = false,
    val error: String? = null
)

