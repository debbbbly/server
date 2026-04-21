package com.debbly.server.pusher.controller

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.pusher.model.PresenceUserInfo
import com.pusher.rest.Pusher
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/pusher")
class PusherAuthController(
    private val pusher: Pusher,
    private val authService: AuthService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/auth")
    fun authenticateChannel(
        @RequestParam("socket_id") socketId: String,
        @RequestParam("channel_name") channelName: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<String> {
        try {
            val userInfo = externalUserId?.let { extUserId ->
                try {
                    authService.authenticateWithLastSeen(extUserId).let { user ->
                        PresenceUserInfo(
                            userId = user.userId,
                            username = user.username ?: "User ${user.userId.take(8)}",
                            avatarUrl = user.avatarUrl,
                            isRegistered = true
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to authenticate user, treating as guest: ${e.message}")
                    generateGuestUserInfo()
                }
            } ?: generateGuestUserInfo()

            // Authenticate based on channel type
            val authResponse = when {
                channelName.startsWith("private-user-") -> {
                    // Private channel - user can only subscribe to their own channel
                    val requestedUserId = channelName.removePrefix("private-user-")
                    if (userInfo.userId == requestedUserId && userInfo.isRegistered) {
                        pusher.authenticate(socketId, channelName)
                    } else {
                        logger.warn("Unauthorized private channel access: user=${userInfo.userId}, channel=$channelName")
                        return ResponseEntity.status(403).body("{\"error\": \"Unauthorized\"}")
                    }
                }

//                channelName.startsWith("presence-stage-hosts-") -> {
//                    // Presence channel for stage hosts only (max 100 members limit is fine for hosts)
//                    val stageId = channelName.removePrefix("presence-stage-hosts-")
//                    // TODO: Add check to verify user is actually a host of this stage
//
//                    pusher.authenticate(
//                        socketId,
//                        channelName,
//                        com.pusher.rest.data.PresenceUser(
//                            userInfo.userId,
//                            mapOf(
//                                "username" to userInfo.username,
//                                "avatarUrl" to (userInfo.avatarUrl ?: ""),
//                                "isRegistered" to userInfo.isRegistered
//                            )
//                        )
//                    )
//                }

                channelName.startsWith("public-") -> {
                    // Public channels don't require authentication
                    logger.info("Public channel access granted: $channelName")
                    return ResponseEntity.status(200).body("{}")
                }

                else -> {
                    logger.warn("Unknown channel type: $channelName")
                    return ResponseEntity.status(400).body("{\"error\": \"Invalid channel\"}")
                }
            }

            return ResponseEntity.ok(authResponse)
        } catch (e: Exception) {
            logger.error("Pusher authentication failed for channel $channelName: ${e.message}", e)
            return ResponseEntity.status(500).body("{\"error\": \"Internal server error\"}")
        }
    }

    private fun generateGuestUserInfo(): PresenceUserInfo {
        val guestId = UUID.randomUUID().toString().take(8)
        return PresenceUserInfo(
            userId = "guest_$guestId",
            username = "Guest $guestId",
            avatarUrl = null,
            isRegistered = false
        )
    }
}
