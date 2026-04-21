package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.resolvers.UserEmail
import com.debbly.server.storage.S3Service
import com.debbly.server.user.repository.SocialUsernameCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import com.debbly.server.user.repository.UserReportJpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import java.time.Clock
import java.time.Instant.now
import java.util.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val onlineUsersService: OnlineUsersService,
    private val userService: UserService,
    private val socialUsernameCachedRepository: SocialUsernameCachedRepository,
    private val usernameService: UsernameService,
    private val userReportRepository: UserReportJpaRepository,
    private val s3Service: S3Service,
    private val clock: Clock
) {

    @GetMapping("/me")
    fun me(
        @ExternalUserId externalUserId: String?,
        @UserEmail email: String?
    ): ResponseEntity<UserMeResponse> {
        if (externalUserId == null || email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: userService.createUser(externalUserId, email)

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
        val result = usernameService.validateUsername(request.username)

        return if (result.valid) {
            ResponseEntity.ok(VerifyUsernameResponse(true))
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                VerifyUsernameResponse(false, errorMessage = result.reason)
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

    /**
     * Client should call this every ~60 seconds to maintain online presence.
     * Refreshes the TTL window in Redis so the user stays "online".
     */
    @PostMapping("/heartbeat")
    fun heartbeat(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        if (externalUserId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()
        onlineUsersService.markUserOnline(user.userId)
        return ResponseEntity.noContent().build()
    }

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

    @DeleteMapping("{userId}")
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
        @AuthenticationPrincipal jwt: Jwt?,
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

        val accessToken = jwt?.tokenValue
        val result = userService.updateUsername(user, request.username, accessToken)

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

    data class CreateAvatarUploadUrlRequest(
        val contentType: String
    )

    data class CreateAvatarUploadUrlResponse(
        val key: String,
        val uploadUrl: String,
        val publicUrl: String,
        val expiresInSeconds: Long
    )

    @PostMapping("{userId}/avatar/upload-url")
    fun createAvatarUploadUrl(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: CreateAvatarUploadUrlRequest
    ): ResponseEntity<CreateAvatarUploadUrlResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val upload = s3Service.generateAvatarUpload(user.userId, request.contentType)
        return ResponseEntity.ok(
            CreateAvatarUploadUrlResponse(
                key = upload.key,
                uploadUrl = upload.uploadUrl,
                publicUrl = upload.publicUrl,
                expiresInSeconds = upload.expiresInSeconds
            )
        )
    }

    data class UpdateAvatarRequest(
        val key: String
    )

    @PutMapping("{userId}/avatar")
    fun updateAvatar(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: UpdateAvatarRequest
    ): ResponseEntity<UpdateAvatarResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val user = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.notFound().build()

        if (user.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = userService.updateAvatar(user, request.key)

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

    @PostMapping("/{userId}/report")
    fun reportUser(
        @PathVariable userId: String,
        @ExternalUserId externalUserId: String?,
        @RequestBody request: ReportUserRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        val rateLimitKey = externalUserId ?: httpRequest.remoteAddr
        if (!ReportRateLimiter.tryConsume(rateLimitKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        val reporter = externalUserId?.let { userCachedRepository.findByExternalUserId(it) }

        if (reporter != null && reporter.userId == userId) {
            return ResponseEntity.badRequest().build()
        }

        if (reporter != null && userReportRepository.existsByReporterUserIdAndReportedUserId(reporter.userId, userId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

        userCachedRepository.findById(userId)
            ?: return ResponseEntity.notFound().build()

        userReportRepository.save(
            UserReportEntity(
                reportId = idService.getId(),
                reporterUserId = reporter?.userId,
                reportedUserId = userId,
                reason = request.reason,
                createdAt = now(clock)
            )
        )

        return ResponseEntity.noContent().build()
    }

    data class ReportUserRequest(
        val reason: ReportReason
    )
}
