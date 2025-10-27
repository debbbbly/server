package com.debbly.server.followers

import com.debbly.server.IdService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class FollowersController(
    private val userCachedRepository: UserCachedRepository,
    private val userFollowService: UserFollowService,
) {

    @PostMapping("/follow")
    fun followUser(
        @ExternalUserId externalUserId: String?,
        @RequestBody request: FollowRequest
    ): ResponseEntity<Void> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val currentUser = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            userFollowService.followUser(currentUser.userId, request.userId)
            ResponseEntity.ok().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/unfollow")
    fun unfollowUser(
        @ExternalUserId externalUserId: String?,
        @RequestBody request: UnfollowRequest
    ): ResponseEntity<Void> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val currentUser = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            userFollowService.unfollowUser(currentUser.userId, request.userId)
            ResponseEntity.ok().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @GetMapping("/follow")
    fun getFollowing(@ExternalUserId externalUserId: String?): ResponseEntity<List<UserSummary>> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val currentUser = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val following = userFollowService.getFollowing(currentUser.userId)
        val response = following.map { user ->
            UserSummary(user.userId, user.username, user.email)
        }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/followers")
    fun getFollowers(@ExternalUserId externalUserId: String?): ResponseEntity<List<UserSummary>> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val currentUser = userCachedRepository.findByExternalUserId(externalUserId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val followers = userFollowService.getFollowers(currentUser.userId)
        val response = followers.map { user ->
            UserSummary(user.userId, user.username, user.email)
        }
        return ResponseEntity.ok(response)
    }

    data class FollowRequest(
        val userId: String
    )

    data class UnfollowRequest(
        val userId: String
    )

    data class UserSummary(
        val id: String,
        val username: String?,
        val email: String
    )
}
