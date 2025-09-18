package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.user.UserValidator.isValidUsername
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/users")
class UserController(
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val onlineUsersService: OnlineUsersService,
) {

    @GetMapping("/me")
    fun me(@ExternalUserId externalUserId: String?): ResponseEntity<UserMeResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: userCachedRepository.save(
                UserModel(
                    userId = idService.getId(),
                    externalUserId = externalUserId,
                    email = "unknown@gmail.com"
                )
            )

        return ResponseEntity.ok(UserMeResponse(user.userId, user.username, user.email, user.birthdate))
    }

    data class UserMeResponse(
        val id: String,
        val username: String?,
        val email: String,
        val birthdate: LocalDate?
    )

    @GetMapping
    fun getUserByUsername(@RequestParam username: String): ResponseEntity<UserPublicResponse> {
        val user = userCachedRepository.findByUsername(username.trim())
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(UserPublicResponse(user.userId, user.username, user.avatarUrl))
    }

    data class UserPublicResponse(
        val id: String,
        val username: String?,
        val avatarUrl: String?
    )

    @PostMapping("/verify-username")
    fun verifyUsername(@RequestBody request: VerifyUsernameRequest): ResponseEntity<VerifyUsernameResponse> {
        if (!isValidUsername(request.username)) {
            return ResponseEntity.badRequest().body(
                VerifyUsernameResponse(false, errorMessage = "Invalid username format")
            )
        }

        val isAvailable = userCachedRepository.findByUsername(request.username.trim()) == null
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

    @GetMapping("/online")
    fun getOnlineUsers(): ResponseEntity<ListUsersResponse> {
        val onlineUsers = onlineUsersService.getOnlineUsers()
        return ResponseEntity.ok(ListUsersResponse(onlineUsers, onlineUsers.size))
    }

    data class ListUsersResponse(
        val users: List<ListUserResponse>,
        val count: Int
    )

    @GetMapping("/top")
    fun getTopUsers(): ResponseEntity<ListUsersResponse> {
        val topUsers = userCachedRepository.findTop100ByRankDesc()
            .map { user ->
                ListUserResponse(
                    userId = user.userId,
                    username = user.username ?: "Anonymous",
                    avatarUrl = user.avatarUrl,
                    rank = user.rank
                )
            }
        return ResponseEntity.ok(ListUsersResponse(topUsers, topUsers.size))
    }

}
