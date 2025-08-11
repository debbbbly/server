package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.user.UserValidator.isValidUsername
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/users")
class UserController(
    private val service: UserCachedRepository,
    private val idService: IdService,
) {

    @GetMapping("/me")
    fun me(@ExternalUserId externalUserId: String?): ResponseEntity<UserResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = service.findByExternalUserId(externalUserId)
            ?: service.save(
                UserEntity(
                    userId = idService.getId(),
                    externalUserId = externalUserId,
                    email = "unknown@gmail.com"
                )
            )

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
