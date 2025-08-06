package com.debbly.server.debates

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/debates/queue")
class DebateQueueController(
    private val debateQueueService: DebateQueueService
) {

    @PostMapping("/join")
    fun joinQueue(@AuthenticationPrincipal principal: Jwt?): ResponseEntity<QueueStatusResponse> {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val userId = getUserId(principal)
        val response = debateQueueService.joinQueue(userId)

        return ResponseEntity.ok(response)
    }

    @GetMapping("/status")
    fun getQueueStatus(@AuthenticationPrincipal principal: Jwt?): ResponseEntity<QueueStatusResponse> {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val userId = getUserId(principal)
        val response = debateQueueService.getStatus(userId)

        return ResponseEntity.ok(response)
    }

    @PostMapping("/leave")
    fun leaveQueue(@AuthenticationPrincipal principal: Jwt?): ResponseEntity<QueueStatusResponse> {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val userId = getUserId(principal)
        val response = debateQueueService.leaveQueue(userId)

        return ResponseEntity.ok(response)
    }

    private fun getUserId(principal: Jwt): String = principal.claims["sub"] as String
}