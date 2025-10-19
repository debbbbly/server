package com.debbly.server.auth.service

import com.debbly.server.auth.UserStatus
import com.debbly.server.config.SupabaseConfigProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.time.Instant

@Service
class SupabaseAuthService(
    private val supabaseConfig: SupabaseConfigProperties,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun signUp(email: String, password: String): SupabaseAuthResponse {
        val url = "${supabaseConfig.authUrl}/signup"
        val headers = createHeaders()

        val request = mapOf(
            "email" to email,
            "password" to password
        )

        return try {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                parseAuthResponse(response.body as Map<String, Any>)
            } else {
                SupabaseAuthResponse(error = "Signup failed")
            }
        } catch (e: RestClientException) {
            logger.error("Signup failed", e)
            SupabaseAuthResponse(error = e.message ?: "Signup failed")
        }
    }

    fun signIn(email: String, password: String): SupabaseAuthResponse {
        val url = "${supabaseConfig.authUrl}/token?grant_type=password"
        val headers = createHeaders()
        val request = mapOf("email" to email, "password" to password)

        return runCatching {
            val entity = HttpEntity(request, headers)
            val response = restTemplate.postForEntity(url, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                parseAuthResponse(response.body as Map<String, Any>)
            } else {
                SupabaseAuthResponse(error = "Signin failed: ${response.statusCode}")
            }
        }.getOrElse { e ->
            logger.error("Signin failed", e)
            SupabaseAuthResponse(error = e.message ?: "Signin failed")
        }
    }

    fun refreshToken(refreshToken: String): SupabaseAuthResponse {
        val url = "${supabaseConfig.authUrl}/token?grant_type=refresh_token"
        val headers = createHeaders()

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
        val url = "${supabaseConfig.authUrl}/logout"
        val headers = createHeaders()
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
        val url = "${supabaseConfig.authUrl}/recover"
        val headers = createHeaders()

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
        val url = "${supabaseConfig.authUrl}/verify"
        val headers = createHeaders()

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
        val url = "${supabaseConfig.authUrl}/resend"
        val headers = createHeaders()

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
        val url = "${supabaseConfig.authUrl}/user"
        val headers = createHeaders()
        headers.setBearerAuth(accessToken)

        return try {
            val entity = HttpEntity<String>(null, headers)
            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)

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

    fun getUserStatusByEmail(email: String): UserStatus {
        val url = "${supabaseConfig.authUrl}/admin/users"
        val headers = createAdminHeaders()

        return try {
            val entity = HttpEntity<String>(null, headers)
            val response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                val responseBody = response.body as? Map<String, Any>
                val users = responseBody?.get("users") as? List<Map<String, Any>>

                val user = users?.find { user ->
                    val userEmail = user["email"] as? String
                    userEmail?.equals(email, ignoreCase = true) == true
                }

                when {
                    user == null -> UserStatus.NOT_FOUND
                    user["email_confirmed_at"] != null -> UserStatus.CONFIRMED
                    else -> UserStatus.UNCONFIRMED
                }
            } else {
                logger.warn("Failed to check user status: ${response.statusCode}")
                UserStatus.NOT_FOUND
            }
        } catch (e: RestClientException) {
            logger.error("Error checking user status for email: $email", e)
            UserStatus.NOT_FOUND
        }
    }

    fun userExistsByEmail(email: String): Boolean {
        return getUserStatusByEmail(email) != UserStatus.NOT_FOUND
    }

    fun updateUserMetadata(accessToken: String, metadata: Map<String, Any>): Boolean {
        val url = "${supabaseConfig.authUrl}/user"
        val headers = createHeaders()
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

    private fun createHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["apikey"] = supabaseConfig.publishableKey
        //headers["Authorization"] = "Bearer ${supabaseConfig.publishableKey}"
        return headers
    }

    private fun createAdminHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["apikey"] = supabaseConfig.secretKey
        headers["Authorization"] = "Bearer ${supabaseConfig.secretKey}"
        return headers
    }

    private fun parseAuthResponse(responseBody: Map<String, Any>): SupabaseAuthResponse {
        val accessToken = responseBody["access_token"] as? String
        val refreshToken = responseBody["refresh_token"] as? String
        val tokenType = responseBody["token_type"] as? String
        val expiresIn = responseBody["expires_in"] as? Int
        val user = responseBody["user"] as? Map<String, Any>

        val expiresAt = if (expiresIn != null) {
            Instant.now().plusSeconds(expiresIn.toLong())
        } else null

        return SupabaseAuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            expiresAt = expiresAt,
            user = user?.let { parseUser(it) },
            success = accessToken != null
        )
    }

    fun initiateGoogleOAuth(redirectUrl: String? = null): SupabaseOAuthResponse {
        val baseUrl = "${supabaseConfig.authUrl}/authorize"
        val params = mutableMapOf(
            "provider" to "google"
        )

        redirectUrl?.let { params["redirect_to"] = it }

        val queryString = params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        val authUrl = "$baseUrl?$queryString"

        return SupabaseOAuthResponse(
            authUrl = authUrl,
            success = true
        )
    }

    private fun parseUser(userData: Map<String, Any>): SupabaseUser {
        val userMetadata = userData["user_metadata"] as? Map<String, Any>
        return SupabaseUser(
            id = userData["id"] as String,
            email = userData["email"] as? String,
            emailConfirmed = userData["email_confirmed_at"] != null,
            createdAt = userData["created_at"] as? String,
            updatedAt = userData["updated_at"] as? String,
            username = userMetadata?.get("username") as? String
        )
    }
}

data class SupabaseAuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Int? = null,
    val expiresAt: Instant? = null,
    val user: SupabaseUser? = null,
    val success: Boolean = false,
    val error: String? = null
)

data class SupabaseOAuthResponse(
    val authUrl: String? = null,
    val success: Boolean = false,
    val error: String? = null
)

data class SupabaseUser(
    val id: String,
    val email: String?,
    val emailConfirmed: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
    val username: String? = null
)

