package com.debbly.server.auth

import com.debbly.server.IdService
import com.debbly.server.auth.AuthErrorCode.*
import com.debbly.server.auth.service.SupabaseAuthService
import com.debbly.server.user.UserService
import com.debbly.server.user.UserValidator.isUserComplete
import com.debbly.server.user.repository.UserCachedRepository
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val supabaseAuthService: SupabaseAuthService,
    private val userCachedRepository: UserCachedRepository,
    private val userService: UserService,
    private val idService: IdService,
    private val jwtDecoder: JwtDecoder,
    private val env: org.springframework.core.env.Environment
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<TokenResponse> {
        return try {
            if (!AuthRateLimiter.tryConsume(req.usernameOrEmail)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(TokenResponse(error = AUTH_TOO_MANY_ATTEMPTS))
            }

            val email = userCachedRepository.findByUsername(req.usernameOrEmail)?.email ?: req.usernameOrEmail

            val authResponse = supabaseAuthService.signIn(email, req.password)

            val userId = authResponse.user?.id
            val userEmail = authResponse.user?.email ?: email

            return when {
                authResponse.error != null || userId == null -> {
                    unauthorized(AUTH_LOGIN_FAILED)
                }

                !isUserComplete(userService.createUser(userId, userEmail)) -> {
                    badRequest(AUTH_USER_INCOMPLETE)
                }

                else -> {
                    ResponseEntity.ok(
                        TokenResponse(
                            accessToken = authResponse.accessToken,
                            idToken = authResponse.accessToken, // deliberate?
                            refreshToken = authResponse.refreshToken
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Login failed", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(TokenResponse(error = AUTH_GENERIC_ERROR))
        }
    }

    private fun unauthorized(error: AuthErrorCode) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(TokenResponse(error = error))

    private fun badRequest(error: AuthErrorCode) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(TokenResponse(error = error))

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<ApiResponse> {
        if (!AuthRateLimiter.tryConsume(request.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AUTH_TOO_MANY_ATTEMPTS))
        }

        try {
            val authResponse = supabaseAuthService.signUp(request.email, request.password)

            return if (authResponse.error != null) {
                if (authResponse.error.contains("already", ignoreCase = true)) {
                    ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse(false, AUTH_EMAIL_ALREADY_REGISTERED))
                } else {
                    ResponseEntity.badRequest().body(ApiResponse(false, AUTH_GENERIC_ERROR))
                }
            } else {
                ResponseEntity.ok(ApiResponse(true))
            }
        } catch (e: Exception) {
            logger.error("Signup failed", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse(false, AUTH_GENERIC_ERROR))
        }
    }

//    @PostMapping("/confirm-email")
//    fun confirmEmail(@RequestBody request: SignupConfirmEmailRequest): ResponseEntity<TokenResponse> {
//        if (!AuthRateLimiter.tryConsume(request.email)) {
//            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
//                .body(TokenResponse(error = AUTH_TOO_MANY_ATTEMPTS))
//        }
//
//        try {
//            val confirmResponse = supabaseAuthService.confirmSignUp(request.code)
//
//            if (confirmResponse.error != null) {
//                return ResponseEntity.status(BAD_REQUEST)
//                    .body(TokenResponse(error = AUTH_CONFIRMATION_CODE_INVALID))
//            }
//
//            val authResponse = supabaseAuthService.signIn(request.email, request.password)
//
//            if (authResponse.error != null || authResponse.user == null) {
//                return ResponseEntity.status(BAD_REQUEST)
//                    .body(TokenResponse(error = AUTH_GENERIC_ERROR))
//            }
//
//            val user = UserModel(
//                userId = idService.getId(),
//                externalUserId = authResponse.user.id,
//                email = request.email,
//                username = request.username.takeIf { isValidUsername(request.username) },
//                birthdate = request.birthdate.takeIf { isValidBirthdate(request.birthdate) },
//            )
//
//            userCachedRepository.save(user)
//
//            return if (!isUserComplete(user)) {
//                ResponseEntity.status(BAD_REQUEST)
//                    .body(TokenResponse(error = AUTH_USER_INCOMPLETE))
//            } else {
//                ResponseEntity.ok(
//                    TokenResponse(
//                        accessToken = authResponse.accessToken,
//                        idToken = authResponse.accessToken,
//                        refreshToken = authResponse.refreshToken
//                    )
//                )
//            }
//        } catch (e: Exception) {
//            logger.error("Email confirmation failed", e)
//            return ResponseEntity.status(BAD_REQUEST)
//                .body(TokenResponse(error = AUTH_GENERIC_ERROR))
//        }
//    }

    @PostMapping("/resend-confirmation")
    fun resendConfirmation(@RequestBody req: EmailRequest): ResponseEntity<ApiResponse> {
        if (!AuthRateLimiter.tryConsume(req.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AUTH_TOO_MANY_ATTEMPTS))
        }

        return try {
            val response = supabaseAuthService.resendConfirmation(req.email)

            if (response.error != null) {
                ResponseEntity.status(BAD_REQUEST)
                    .body(ApiResponse(false, AUTH_RESEND_CONFIRMATION_FAILED))
            } else {
                ResponseEntity.ok(ApiResponse(true))
            }
        } catch (e: Exception) {
            logger.error("Resend confirmation failed", e)
            ResponseEntity.status(BAD_REQUEST)
                .body(ApiResponse(false, AUTH_RESEND_CONFIRMATION_FAILED))
        }
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody req: EmailRequest): ResponseEntity<ApiResponse> {
        if (!AuthRateLimiter.tryConsume(req.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AUTH_TOO_MANY_ATTEMPTS))
        }

        return try {
            val response = supabaseAuthService.resetPassword(req.email)

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
    fun resetPassword(@RequestBody req: ResetPasswordRequest): ResponseEntity<ApiResponse> {
        return try {
            val response = supabaseAuthService.confirmSignUp(req.code, "recovery")

            if (response.error != null) {
                ResponseEntity.status(BAD_REQUEST)
                    .body(ApiResponse(false, AUTH_RESET_PASSWORD_FAILED))
            } else {
                ResponseEntity.ok(ApiResponse(true))
            }
        } catch (e: Exception) {
            logger.error("Reset password failed", e)
            ResponseEntity.status(BAD_REQUEST)
                .body(ApiResponse(false, AUTH_RESET_PASSWORD_FAILED))
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
                supabaseAuthService.signOut(tokenToUse)
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
            val response = supabaseAuthService.refreshToken(refreshToken)

            if (response.error != null) {
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

    @PostMapping("/callback")
    fun callback(
        @RequestBody request: CallbackRequest,
        response: HttpServletResponse
    ): ResponseEntity<CallbackResponse> {
        return try {
            logger.info("Processing auth callback for token type: ${request.type}")

            val user = supabaseAuthService.validateToken(request.access_token)
                ?: return ResponseEntity.status(UNAUTHORIZED)
                    .body(CallbackResponse(false, error = AUTH_CALLBACK_INVALID_TOKEN))

            logger.info("Token validated for user: ${user.email}")

            val userModel = try {
                userService.createUser(user.id, user.email ?: "")
            } catch (e: Exception) {
                logger.error("Failed to create/get user", e)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CallbackResponse(false, error = AUTH_CALLBACK_USER_CREATION_FAILED))
            }

            val secure = "dev" !in env.activeProfiles
            response.setCookie("accessToken", request.access_token, 60 * 60, "Lax", secure)
            response.setCookie("idToken", request.access_token, 60 * 60, "Lax", secure)
            request.refresh_token?.let { refreshToken ->
                response.setCookie("refreshToken", refreshToken, 60 * 60 * 24 * 30, "Strict", secure)
            }

            val callbackUser = CallbackUserInfo(
                userId = userModel.userId,
                email = userModel.email,
                username = userModel.username,
                isComplete = isUserComplete(userModel)
            )

            logger.info("Auth callback successful for user: ${userModel.email}")
            ResponseEntity.ok(CallbackResponse(true, user = callbackUser))

        } catch (e: Exception) {
            logger.error("Auth callback failed", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CallbackResponse(false, error = AUTH_GENERIC_ERROR))
        }
    }

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

//    data class SignupConfirmEmailRequest(
//        @field:NotBlank val email: String,
//        @field:NotBlank val password: String,
//        val code: String,
//        @field:NotBlank val username: String,
//        val birthdate: LocalDate
//    )

    data class EmailRequest(@field:NotBlank val email: String)

    data class ResetPasswordRequest(
        @field:NotBlank val email: String,
        @field:NotBlank val code: String,
        @field:NotBlank val newPassword: String
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

    data class SetCookie(
        val accessToken: String,
        val idToken: String,
        val refreshToken: String,
    )

    data class RefreshTokenRequest(
        @field:NotBlank val refreshToken: String
    )

    data class CallbackRequest(
        @field:NotBlank val access_token: String,
        val refresh_token: String?,
        val expires_at: String?,
        val expires_in: String?,
        val token_type: String?,
        val type: String?
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

}
