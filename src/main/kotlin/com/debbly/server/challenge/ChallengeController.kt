package com.debbly.server.challenge

import com.debbly.server.auth.resolvers.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.infra.error.ForbiddenException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/challenges")
class ChallengeController(
    private val challengeService: ChallengeService,
    private val authService: AuthService,
) {

    data class CreateChallengeRequest(
        val claimId: String,
        val stance: ClaimStance,
    )

    @PostMapping
    fun create(
        @ExternalUserId externalUserId: String?,
        @RequestBody request: CreateChallengeRequest,
    ): ResponseEntity<ChallengeResponse> {
        val user = authService.authenticate(externalUserId)
        if (user.banned) throw ForbiddenException("Your account is limited")
        val response = challengeService.create(user, request.claimId, request.stance)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{challengeId}")
    fun getById(@PathVariable challengeId: String): ResponseEntity<ChallengeResponse> {
        return ResponseEntity.ok(challengeService.getById(challengeId))
    }

    @PostMapping("/{challengeId}/cancel")
    fun cancel(
        @ExternalUserId externalUserId: String?,
        @PathVariable challengeId: String,
    ): ResponseEntity<ChallengeResponse> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(challengeService.cancel(user, challengeId))
    }

    @PostMapping("/{challengeId}/accept")
    fun accept(
        @ExternalUserId externalUserId: String?,
        @PathVariable challengeId: String,
    ): ResponseEntity<ChallengeResponse> {
        val user = authService.authenticate(externalUserId)
        if (user.banned) throw ForbiddenException("Your account is limited")

        return ResponseEntity.ok(challengeService.accept(user, challengeId))
    }
}
