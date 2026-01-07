package com.debbly.server.auth

import com.debbly.server.auth.AuthErrorCode.*
import com.debbly.server.auth.service.AuthService
import com.debbly.server.user.UserService
import com.debbly.server.user.UserValidator.isUserComplete
import com.debbly.server.user.repository.UserCachedRepository
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant.now

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val userCachedRepository: UserCachedRepository,
    private val userService: UserService,
    private val env: Environment,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private fun unauthorized(error: AuthErrorCode) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(TokenResponse(error = error))

    private fun badRequest(error: AuthErrorCode) =
        ResponseEntity.status(BAD_REQUEST).body(TokenResponse(error = error))

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<UnifiedAuthResponse> {
        if (!AuthRateLimiter.tryConsume(request.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(UnifiedAuthResponse(error = AUTH_TOO_MANY_ATTEMPTS))
        }

        try {
            val signInResponse = authService.signIn(request.email, request.password)

            if (signInResponse.accessToken != null && signInResponse.user != null) {
                val userId = signInResponse.user.id
                val userEmail = signInResponse.user.email ?: request.email

                val user = userCachedRepository.findById(userId) ?: userService.createUser(userId, userEmail)
                userCachedRepository.save(
                    user.copy(
                        lastLogin = now(clock),
                        lastSeen = now(clock)
                    )
                )

                return ResponseEntity.ok(
                    UnifiedAuthResponse(
                        accessToken = signInResponse.accessToken,
                        idToken = signInResponse.accessToken,
                        refreshToken = signInResponse.refreshToken,
                        signedUp = false,
                        success = true
                    )
                )
            }

            val signUpResponse = authService.signUp(request.email, request.password)

            val externalUserId = signUpResponse.user?.id ?: let {
                logger.warn("Sign-up failed: missing user id, response={}", signUpResponse)
                return ResponseEntity.badRequest()
                    .body(UnifiedAuthResponse(error = AUTH_GENERIC_ERROR))
            }

            // if user is missing by externalUserId - invalid credentials of existing user have been used
            authService.getUserById(externalUserId)
                ?: return ResponseEntity.status(BAD_REQUEST)
                    .body(UnifiedAuthResponse(error = AUTH_INVALID_CREDENTIALS))

            return ResponseEntity.ok(
                UnifiedAuthResponse(
                    signedUp = true,
                    success = true
                )
            )

        } catch (e: Exception) {
            logger.error("Sign-up failed.", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UnifiedAuthResponse(error = AUTH_GENERIC_ERROR))
        }
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody req: EmailRequest): ResponseEntity<ApiResponse> {
        if (!AuthRateLimiter.tryConsume(req.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AUTH_TOO_MANY_ATTEMPTS))
        }

        return try {
            val response = authService.resetPassword(req.email)

            if (response.error != null) {
                ResponseEntity.status(BAD_REQUEST)
                    .body(ApiResponse(false, AUTH_FORGOT_PASSWORD_FAILED))
            } else {
                ResponseEntity.ok(ApiResponse(true))
            }
        } catch (e: Exception) {
            logger.error("Forgot password failed", e)
            ResponseEntity.status(BAD_REQUEST)
                .body(ApiResponse(false, AUTH_FORGOT_PASSWORD_FAILED))
        }
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @RequestBody request: ResetPasswordRequest,
        response: HttpServletResponse
    ): ResponseEntity<CallbackResponse> {
        try {

            val passwordUpdated = authService.updatePassword(request.accessToken, request.newPassword)

            if (!passwordUpdated) {
                logger.error("Failed to update password after successful token verification")
                return ResponseEntity.status(BAD_REQUEST)
                    .body(CallbackResponse(success = false, error = AUTH_RESET_PASSWORD_FAILED))
            }

            setCookie(
                request.accessToken,
                request.refreshToken,
                response
            )

            return ResponseEntity.ok(CallbackResponse(true))

        } catch (e: Exception) {
            return ResponseEntity.status(BAD_REQUEST)
                .body(CallbackResponse(success = false, error = AUTH_RESET_PASSWORD_FAILED))
        }
    }

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal accessToken: Jwt?,
        @CookieValue("accessToken", required = false) cookieAccessToken: String?,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse> {

        val tokenToUse = accessToken?.tokenValue ?: cookieAccessToken

        if (tokenToUse != null) {
            try {
                authService.signOut(tokenToUse)
            } catch (e: Exception) {
                logger.warn("Failed to sign out from Supabase", e)
            }
        }

        val secure = "dev" !in env.activeProfiles
        response.setCookie("accessToken", "", 0, "Lax", secure)
        response.setCookie("idToken", "", 0, "Lax", secure)
        response.setCookie("refreshToken", "", 0, "Strict", secure)

        return ResponseEntity.ok(ApiResponse(true))
    }

    @PostMapping("/refresh")
    fun refreshToken(@CookieValue("refreshToken") refreshToken: String): ResponseEntity<TokenResponse> {
        return try {
            logger.info(
                "Attempting to refresh token. Token length: ${refreshToken.length}, first 20 chars: ${
                    refreshToken.take(
                        20
                    )
                }..."
            )
            val response = authService.refreshToken(refreshToken)

            if (response.error != null) {
                logger.warn("Refresh token failed with error: ${response.error}")
                ResponseEntity.status(UNAUTHORIZED).body(TokenResponse(error = AUTH_REFRESH_TOKEN_INVALID))
            } else {
                ResponseEntity.ok(
                    TokenResponse(
                        accessToken = response.accessToken,
                        idToken = response.accessToken,
                        refreshToken = response.refreshToken ?: refreshToken
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Token refresh failed", e)
            ResponseEntity.status(UNAUTHORIZED).body(TokenResponse(error = AUTH_REFRESH_TOKEN_INVALID))
        }
    }

    /**
     * Generic callback endpoint for Supabase redirects (email confirmation, password reset, etc.)
     * This endpoint receives tokens from Supabase after various authentication flows
     */
    @PostMapping("/callback")
    fun callback(
        @RequestBody request: SupabaseCallbackRequest,
        response: HttpServletResponse
    ): ResponseEntity<CallbackResponse> {
        return try {
            val supabaseUser = authService.validateToken(request.accessToken)
                ?: return ResponseEntity.status(UNAUTHORIZED)
                    .body(CallbackResponse(false, error = AUTH_CALLBACK_INVALID_TOKEN))

            val user = userCachedRepository.findById(supabaseUser.id)
                ?: userService.createUser(supabaseUser.id, supabaseUser.email)
            userCachedRepository.save(
                user.copy(
                    lastLogin = now(clock),
                    lastSeen = now(clock)
                )
            )

            setCookie(
                request.accessToken,
                request.refreshToken,
                response
            )

            val callbackUser = CallbackUserInfo(
                userId = user.userId,
                email = user.email,
                username = user.username,
                isComplete = isUserComplete(user)
            )

            ResponseEntity.ok(CallbackResponse(true, user = callbackUser))

        } catch (e: Exception) {
            logger.error("Sign-up failed.", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CallbackResponse(false, error = AUTH_GENERIC_ERROR))
        }
    }

    private fun setCookie(
        accessToken: String,
        refreshToken: String,
        response: HttpServletResponse
    ) {
        val secure = "dev" !in env.activeProfiles
        response.setCookie("accessToken", accessToken, 60 * 60, "Lax", secure)
        response.setCookie("idToken", accessToken, 60 * 60, "Lax", secure)
        response.setCookie("refreshToken", refreshToken, 60 * 60 * 24 * 30, "Strict", secure)
    }

    /**
     * Generic OAuth callback endpoint that handles tokens from any OAuth provider (Google, GitHub, Facebook, etc.)
     * This endpoint receives tokens from Supabase after successful OAuth authentication
     */
    @PostMapping("/oauth/callback")
    fun oauthCallback(
        @RequestBody request: OAuthCallbackRequest,
        response: HttpServletResponse
    ): ResponseEntity<CallbackResponse> {
        return try {
            logger.info("Processing OAuth callback with tokens from Supabase (provider: ${request.provider ?: "unknown"})")

            val user = authService.validateToken(request.accessToken)
                ?: return ResponseEntity.status(UNAUTHORIZED)
                    .body(CallbackResponse(false, error = AUTH_CALLBACK_INVALID_TOKEN))

            val userModel = try {
                userService.createUser(user.id, user.email)
            } catch (e: Exception) {
                logger.error("Failed to create/get user", e)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CallbackResponse(false, error = AUTH_CALLBACK_USER_CREATION_FAILED))
            }

            userCachedRepository.save(
                userModel.copy(
                    lastLogin = now(clock),
                    lastSeen = now(clock)
                )
            )

            // 3. Set authentication cookies
            val secure = "dev" !in env.activeProfiles
            response.setCookie("accessToken", request.accessToken, 60 * 60, "Lax", secure)
            response.setCookie(
                "idToken",
                request.accessToken,
                60 * 60,
                "Lax",
                secure
            ) // accessToken IS the idToken in Supabase
            request.refreshToken?.let { refreshToken ->
                logger.info("Setting refresh token cookie. Token length: ${refreshToken.length}")
                response.setCookie("refreshToken", refreshToken, 60 * 60 * 24 * 30, "Strict", secure)
            } ?: logger.warn("No refresh token provided in OAuth callback")

            val callbackUser = CallbackUserInfo(
                userId = userModel.userId,
                email = userModel.email,
                username = userModel.username,
                isComplete = isUserComplete(userModel)
            )

            logger.info("OAuth callback successful for user: ${userModel.email}")
            ResponseEntity.ok(CallbackResponse(true, user = callbackUser))

        } catch (e: Exception) {
            logger.error("OAuth callback failed", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CallbackResponse(false, error = AUTH_GENERIC_ERROR))
        }
    }

    data class SupabaseCallbackRequest(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: String?,
    )

    data class OAuthCallbackRequest(
        val accessToken: String,
        val refreshToken: String?,
        val expiresIn: Int?,
        val provider: String? = null  // Optional: to track which OAuth provider was used
    )

    /**
     * Sets authentication cookies from already-validated tokens.
     *
     * SECURITY NOTE: This endpoint does NOT validate tokens. It should ONLY be called:
     * 1. After /auth/login or /auth/signup where tokens were validated by the backend
     * 2. From the frontend to convert response tokens into HTTP-only cookies
     *
     * For OAuth flows, use /oauth/callback instead which validates tokens with Supabase first.
     */
    @PostMapping("/set-cookie")
    fun setCookie(
        @RequestBody request: SetCookie,
        response: HttpServletResponse
    ): ResponseEntity<Void> {
        val secure = "dev" !in env.activeProfiles
        response.setCookie("accessToken", request.accessToken, 60 * 60, "Lax", secure)
        response.setCookie("idToken", request.idToken, 60 * 60, "Lax", secure)
        response.setCookie("refreshToken", request.refreshToken, 60 * 60 * 24 * 30, "Strict", secure)

        return ResponseEntity.ok().build()
    }

    @PostMapping("/google/signin")
    fun googleSignIn(@RequestBody request: GoogleOAuthRequest): ResponseEntity<GoogleOAuthResponse> {
        return try {
            val oauthResponse = authService.initiateGoogleOAuth(request.redirectUrl)

            if (oauthResponse.success && oauthResponse.authUrl != null) {
                ResponseEntity.ok(GoogleOAuthResponse(authUrl = oauthResponse.authUrl))
            } else {
                ResponseEntity.status(BAD_REQUEST)
                    .body(GoogleOAuthResponse(error = AUTH_OAUTH_INITIATION_FAILED))
            }
        } catch (e: Exception) {
            logger.error("Google OAuth initiation failed", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(GoogleOAuthResponse(error = AUTH_GENERIC_ERROR))
        }
    }

    fun HttpServletResponse.setCookie(
        name: String,
        value: String?,
        maxAge: Int,
        sameSite: String = "Lax",
        secure: Boolean
    ) {
        val httpOnly = if (secure) "HttpOnly; " else ""
        val cookieValue =
            "$name=$value; Path=/; ${httpOnly}Max-Age=$maxAge; ${if (secure) "Secure; " else ""}SameSite=$sameSite"
        this.addHeader("Set-Cookie", cookieValue)
    }

    data class ApiResponse(val success: Boolean, val error: AuthErrorCode? = null)

    data class EmailRequest(@field:NotBlank val email: String)

    data class ResetPasswordRequest(
        @field:NotBlank val accessToken: String,
        @field:NotBlank val refreshToken: String,
        @field:NotBlank val expiresIn: String,
        @field:NotBlank val newPassword: String,
    )

    data class SignupRequest(
        @field:NotBlank val email: String,
        @field:NotBlank val password: String,
//        @field:NotBlank val username: String,
//        val birthdate: LocalDate
    )

    data class LoginRequest(
        @field:NotBlank val usernameOrEmail: String,
        @field:NotBlank val password: String
    )

    data class TokenResponse(
        val accessToken: String? = null,
        val idToken: String? = null,
        val refreshToken: String? = null,
        val error: AuthErrorCode? = null
    )

    data class UnifiedAuthResponse(
        val accessToken: String? = null,
        val idToken: String? = null,
        val refreshToken: String? = null,

        val signedUp: Boolean? = null,

        val success: Boolean = false,
        val error: AuthErrorCode? = null
    )

    data class SetCookie(
        val accessToken: String,
        val idToken: String,
        val refreshToken: String,
    )

    data class RefreshTokenRequest(
        @field:NotBlank val refreshToken: String
    )


    data class CallbackResponse(
        val success: Boolean,
        val user: CallbackUserInfo? = null,
        val error: AuthErrorCode? = null
    )

    data class CallbackUserInfo(
        val userId: String,
        val email: String,
        val username: String?,
        val isComplete: Boolean
    )

    data class GoogleOAuthRequest(
        val redirectUrl: String?
    )

    data class GoogleOAuthResponse(
        val authUrl: String? = null,
        val error: AuthErrorCode? = null
    )

}
