package com.debbly.server.user

import com.debbly.server.user.UserValidator.isValidUsername
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/users")
class UserController(private val service: UserService) {


    //Disable CSRF (for stateless APIs)	If you're not using cookies, disable CSRF in the security config

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: Jwt?): ResponseEntity<UserResponse> {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val userId = principal.claims["sub"] as String
        val email = (principal.claims["email"] as String?).orEmpty()

        val user = service.findById(userId) ?: service.create(UserEntity(userId = userId, email = email))

        return ResponseEntity.ok(UserResponse(user.userId, user.username, user.email, user.birthdate))
    }

    data class UserResponse(
        val id: String,
        val username: String?,
        val email: String,
        val birthdate: LocalDate?
    )

    @PostMapping("/verify-username")
    fun verifyUsername(@RequestBody request: VerifyUsernameRequest): ResponseEntity<VerifyUsernameResponse> {
        if (!isValidUsername(request.username)) {
            return ResponseEntity.badRequest().body(
                VerifyUsernameResponse(false, errorMessage = "Invalid username format")
            )
        }

        val isAvailable = service.findByUsername(request.username.trim()) == null
        return if (isAvailable) {
            ResponseEntity.ok(VerifyUsernameResponse(true))
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                VerifyUsernameResponse(false, errorMessage = "Username is already taken")
            )
        }
    }

    data class VerifyUsernameRequest(
        val username: String
    )

    data class VerifyUsernameResponse(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}
