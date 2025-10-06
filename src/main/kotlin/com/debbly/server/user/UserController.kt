package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.user.UserValidator.isValidUsername
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.SocialUsernameCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val onlineUsersService: OnlineUsersService,
    private val userService: UserService,
    private val socialUsernameCachedRepository: SocialUsernameCachedRepository
) {

    @GetMapping("/me")
    fun me(@ExternalUserId externalUserId: String?): ResponseEntity<UserMeResponse> {
        // Temporary: log the externalUserId to debug JWT validation
        println("DEBUG: externalUserId = $externalUserId")
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

        return ResponseEntity.ok(UserMeResponse(user.userId, user.username, user.email, user.avatarUrl))
    }

    data class UserMeResponse(
        val id: String,
        val username: String?,
        val email: String,
        val avatarUrl: String?
    )

    @GetMapping
    fun getUserByUsername(@RequestParam username: String): ResponseEntity<UserPublicResponse> {
        val user = userCachedRepository.findByUsername(username.trim())
            ?: return ResponseEntity.notFound().build()

        val socials = socialUsernameCachedRepository.findAllByUserId(user.userId)
            .associate { it.socialType to it.username }

        return ResponseEntity.ok(UserPublicResponse(user.userId, user.username, user.avatarUrl, user.bio, socials))
    }

    data class UserPublicResponse(
        val id: String,
        val username: String?,
        val avatarUrl: String?,
        val bio: String?,
        val socials: Map<SocialType, String>?
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
            .filter { !it.deleted }
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

    @DeleteMapping("{userId}/delete")
    fun deleteUser(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<DeleteUserResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (user.deleted) {
            return ResponseEntity.status(HttpStatus.GONE).body(
                DeleteUserResponse(false, "User is already deleted")
            )
        }

        val username = UUID.randomUUID().toString().substring(0, 6)

        user.deleted = true
        user.username = "deleted_$username"
        user.email = "deleted_$username@deleted.com"
        user.avatarUrl = null

        userCachedRepository.save(user)

        return ResponseEntity.ok(DeleteUserResponse(true, "User successfully deleted"))
    }

    data class DeleteUserResponse(
        val success: Boolean,
        val message: String
    )

    @PutMapping("{userId}/username")
    fun updateUsername(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: UpdateUsernameRequest
    ): ResponseEntity<UpdateUsernameResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = userService.updateUsername(user, request.username)

        return if (result.success) {
            ResponseEntity.ok(UpdateUsernameResponse(true, result.message))
        } else {
            val status = when {
                result.message.contains("already taken") -> HttpStatus.CONFLICT
                result.message.contains("violates platform rules") -> HttpStatus.FORBIDDEN
                else -> HttpStatus.BAD_REQUEST
            }
            ResponseEntity.status(status).body(UpdateUsernameResponse(false, result.message))
        }
    }

    data class UpdateUsernameRequest(
        val username: String
    )

    data class UpdateUsernameResponse(
        val success: Boolean,
        val message: String
    )

    @PutMapping("{userId}/avatar")
    fun updateAvatar(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<UpdateAvatarResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = userService.updateAvatar(user, file)

        return if (result.success) {
            ResponseEntity.ok(UpdateAvatarResponse(true, result.message, result.avatarUrl))
        } else {
            val status = when {
                result.message.contains("violates platform rules") -> HttpStatus.FORBIDDEN
                else -> HttpStatus.BAD_REQUEST
            }
            ResponseEntity.status(status).body(UpdateAvatarResponse(false, result.message, null))
        }
    }

    data class UpdateAvatarResponse(
        val success: Boolean,
        val message: String,
        val avatarUrl: String?
    )

    @PutMapping("{userId}/bio")
    fun updateBio(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: UpdateBioRequest
    ): ResponseEntity<UpdateBioResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = userService.updateBio(user, request.bio)

        return if (result.success) {
            ResponseEntity.ok(UpdateBioResponse(true, result.message))
        } else {
            val status = when {
                result.message.contains("violates platform rules") -> HttpStatus.FORBIDDEN
                else -> HttpStatus.BAD_REQUEST
            }
            ResponseEntity.status(status).body(UpdateBioResponse(false, result.message))
        }
    }

    data class UpdateBioRequest(
        val bio: String
    )

    data class UpdateBioResponse(
        val success: Boolean,
        val message: String
    )

    @PutMapping("{userId}/socials")
    fun updateSocialUsernames(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: UpdateSocialUsernamesRequest
    ): ResponseEntity<UpdateSocialUsernamesResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = userService.updateSocialUsernames(user.userId, request.usernames)

        return if (result.success) {
            ResponseEntity.ok(UpdateSocialUsernamesResponse(true, result.message))
        } else {
            ResponseEntity.badRequest().body(UpdateSocialUsernamesResponse(false, result.message))
        }
    }

    data class UpdateSocialUsernamesRequest(
        val usernames: Map<SocialType, String>
    )

    data class UpdateSocialUsernamesResponse(
        val success: Boolean,
        val message: String
    )
}
