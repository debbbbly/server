package com.debbly.server.auth

import com.debbly.server.user.UserEntity
import com.debbly.server.user.UserService
import com.debbly.server.user.UserValidator.isUserComplete
import com.debbly.server.user.UserValidator.isValidBirthdate
import com.debbly.server.user.UserValidator.isValidUsername
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.*
import java.time.LocalDate


@RestController
@RequestMapping("/auth")
class AuthController(
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoConfig: CognitoConfig,
    private val userService: UserService

) {

    private val jwtDecoder = NimbusJwtDecoder.withJwkSetUri(cognitoConfig.jwks).build()

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): ResponseEntity<TokenResponse> {
        return try {
            if (!AuthRateLimiter.tryConsume(req.usernameOrEmail)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(TokenResponse(error = AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS))
            }

            val email = userService.findByUsername(req.usernameOrEmail)?.email ?: req.usernameOrEmail

            val authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(cognitoConfig.clientId)
                .authParameters(
                    mapOf(
                        "USERNAME" to email,
                        "PASSWORD" to req.password
                    )
                )
                .build()

            val tokens = cognitoClient.initiateAuth(authRequest).authenticationResult()

            val userId = getUserId(tokens.idToken())
            val user = userService.findById(userId)
                ?: userService.create(
                    UserEntity(
                        userId = userId,
                        email = getEmail(tokens.idToken())
                    )
                )

            return if (!isUserComplete(user)) {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TokenResponse(error = AuthErrorCode.AUTH_USER_INCOMPLETE))

            } else {
                ResponseEntity.ok(
                    TokenResponse(
                        accessToken = tokens.accessToken(),
                        idToken = tokens.idToken(),
                        refreshToken = tokens.refreshToken()
                    )
                )
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(TokenResponse(error = AuthErrorCode.AUTH_LOGIN_FAILED))
        }
    }

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<ApiResponse> {
        if (!isValidUsername(request.username)) {
            return ResponseEntity.badRequest().body(ApiResponse(false, AuthErrorCode.AUTH_INVALID_USERNAME))
        }

        if (!isValidBirthdate(request.birthdate)) {
            return ResponseEntity.badRequest().body(ApiResponse(false, AuthErrorCode.AUTH_INVALID_BIRTHDATE))
        }

        if (!AuthRateLimiter.tryConsume(request.username)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS))
        }

        try {
            val attrs = listOf(
                AttributeType.builder().name("email").value(request.email).build(),
            )

            val signUpRequest = SignUpRequest.builder()
                .clientId(cognitoConfig.clientId)
                .username(request.email)
                .password(request.password)
                .userAttributes(attrs)
                .build()

            cognitoClient.signUp(signUpRequest)

            return ResponseEntity.ok(ApiResponse(true))
        } catch (e: UsernameExistsException) {
            val cognitoUser = cognitoClient.adminGetUser {
                it.userPoolId(cognitoConfig.userPoolId).username(request.email)
            }

            return if (cognitoUser.userStatus() == UserStatusType.UNCONFIRMED) {

                cognitoClient.resendConfirmationCode {
                    it.clientId(cognitoConfig.clientId).username(request.email)
                }

                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse(true, AuthErrorCode.AUTH_EMAIL_REGISTERED_UNCONFIRMED))
            } else {
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse(false, AuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED))
            }
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse(false, AuthErrorCode.AUTH_GENERIC_ERROR))
        }
    }

    @PostMapping("/confirm-email")
    fun confirmEmail(@RequestBody request: SignupConfirmEmailRequest): ResponseEntity<TokenResponse> {
        if (!AuthRateLimiter.tryConsume(request.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(TokenResponse(error = AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS))
        }

        try {
            val confirm = ConfirmSignUpRequest.builder()
                .clientId(cognitoConfig.clientId)
                .username(request.email)
                .confirmationCode(request.code)
                .build()

            cognitoClient.confirmSignUp(confirm)

        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(TokenResponse(error = AuthErrorCode.AUTH_CONFIRMATION_CODE_INVALID))
        }

        return try {
            val authRequest = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .clientId(cognitoConfig.clientId)
                .authParameters(
                    mapOf(
                        "USERNAME" to request.email,
                        "PASSWORD" to request.password
                    )
                )
                .build()

            val authResult = cognitoClient.initiateAuth(authRequest).authenticationResult()

            val user = UserEntity(
                userId = getUserId(authResult.idToken()),
                email = request.email,
                username = request.username.takeIf { isValidUsername(request.username) },
                birthdate = request.birthdate.takeIf { isValidBirthdate(request.birthdate) },
            )

            userService.create(user)

            return if (!isUserComplete(user)) {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TokenResponse(error = AuthErrorCode.AUTH_USER_INCOMPLETE))
            } else {

                ResponseEntity.ok(
                    TokenResponse(
                        accessToken = authResult.accessToken(),
                        idToken = authResult.idToken(),
                        refreshToken = authResult.refreshToken()
                    )
                )
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(TokenResponse(error = AuthErrorCode.AUTH_GENERIC_ERROR))
        }
    }

    @PostMapping("/resend-confirmation")
    fun resendConfirmation(@RequestBody req: EmailRequest): ResponseEntity<ApiResponse> {
        if (!AuthRateLimiter.tryConsume(req.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS))
        }

        return try {
            val resend = ResendConfirmationCodeRequest.builder()
                .clientId(cognitoConfig.clientId)
                .username(req.email)
                .build()

            cognitoClient.resendConfirmationCode(resend)
            ResponseEntity.ok(ApiResponse(true))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse(false, AuthErrorCode.RESEND_CONFIRMATION_FAILED))
        }
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody req: EmailRequest): ResponseEntity<ApiResponse> {
        if (!AuthRateLimiter.tryConsume(req.email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse(false, error = AuthErrorCode.AUTH_TOO_MANY_ATTEMPTS))
        }

        return try {
            val forgot = ForgotPasswordRequest.builder()
                .clientId(cognitoConfig.clientId)
                .username(req.email)
                .build()

            cognitoClient.forgotPassword(forgot)
            ResponseEntity.ok(ApiResponse(true))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(false, AuthErrorCode.FORGOT_PASSWORD_FAILED))
        }
    }

    @PostMapping("/reset-password")
    fun resetPassword(@RequestBody req: ResetPasswordRequest): ResponseEntity<ApiResponse> {
        return try {
            val confirm = ConfirmForgotPasswordRequest.builder()
                .clientId(cognitoConfig.clientId)
                .username(req.email)
                .confirmationCode(req.code)
                .password(req.newPassword)
                .build()

            cognitoClient.confirmForgotPassword(confirm)
            ResponseEntity.ok(ApiResponse(true))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(false, AuthErrorCode.RESET_PASSWORD_FAILED))
        }
    }

    @PostMapping("/logout")
    fun logout(@RequestBody req: LogoutRequest): ResponseEntity<ApiResponse> {
        try {
            cognitoClient.globalSignOut {
                it.accessToken(req.accessToken)
            }
            return ResponseEntity.ok(ApiResponse(true))
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(false, AuthErrorCode.LOGOUT_FAILED))
        }
    }

    private fun getUserId(idToken: String): String {
        return jwtDecoder.decode(idToken).claims["sub"] as String
    }

    private fun getEmail(idToken: String): String {
        return jwtDecoder.decode(idToken).claims["email"] as String
    }

    data class ApiResponse(val success: Boolean, val error: AuthErrorCode? = null)

    data class SignupConfirmEmailRequest(
        @field:NotBlank val email: String,
        @field:NotBlank val password: String,
        val code: String,
        @field:NotBlank val username: String,
        val birthdate: LocalDate
    )

    data class EmailRequest(@field:NotBlank val email: String)

    data class ResetPasswordRequest(
        @field:NotBlank val email: String,
        @field:NotBlank val code: String,
        @field:NotBlank val newPassword: String
    )

    data class SignupRequest(
        @field:NotBlank val email: String,
        @field:NotBlank val password: String,
        @field:NotBlank val username: String,
        val birthdate: LocalDate
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

    data class LogoutRequest(
        @field:NotBlank val accessToken: String
    )

}
