package com.debbly.server.user

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import java.time.LocalDate

@RestController
@RequestMapping("/users")
class UserResource(private val service: UserService) {


    //Disable CSRF (for stateless APIs)	If you're not using cookies, disable CSRF in the security config

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: Jwt): UserResponse {
        val sub = principal.claims["sub"] as String
        val email = principal.claims["email"] as String?

        // You can now query your database:
        //val user = userRepository.findById(sub) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return UserResponse("user.id", "user.username", email, null)
    }

    data class UserResponse(
        val id: String,
        val username: String,
        val email: String?,
        val birthdate: LocalDate?
    )

    @PostMapping("/verify-username")
    fun verifyUsername(@RequestBody request: VerifyUsernameRequest): ResponseEntity<VerifyUsernameResponse> {
        val trimmed = request.username.trim()
        if (trimmed.length !in 5..30 || !trimmed.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return ResponseEntity.badRequest().body(
                VerifyUsernameResponse(false, errorMessage = "Invalid username format")
            )
        }

        val isAvailable = !service.existsByUsername(trimmed)
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
