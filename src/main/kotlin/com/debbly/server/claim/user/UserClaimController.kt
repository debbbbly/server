package com.debbly.server.claim.user

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.claim.ClaimService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/user-claims")
class UserClaimController(
    private val service: ClaimService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService
) {

    @GetMapping
    fun getUserClaims(
        @RequestParam(defaultValue = "5") limit: Int,
        @ExternalUserId externalUserId: String?
    ): List<UserClaimModel> {
        return authService.authenticate(externalUserId)
            .let { user -> service.getUserClaims(user.userId, limit) }
    }

    @PostMapping("/{claimId}/stance")
    fun updateUserClaimStance(
        @PathVariable claimId: String,
        @RequestBody request: UpdateUserClaimStance,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            userClaimService.updateUserClaimStance(user.userId, claimId, request.stance)
        }

        return ResponseEntity.ok().build()
    }

    data class UpdateUserClaimStance(
        val stance: ClaimStance
    )

    @PostMapping("/{claimId}/remove")
    fun removeUserClaim(
        @PathVariable claimId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            userClaimService.removeUserClaim(user.userId, claimId)
        }

        return ResponseEntity.ok().build()
    }

    data class PriorityUpdate(
        val claimId: String,
        val priority: Int
    )

    data class UpdatePrioritiesRequest(
        val priorityUpdates: List<PriorityUpdate>
    )

    @PostMapping("/priority")
    fun updatePriorities(
        @RequestBody request: UpdatePrioritiesRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            userClaimService.updatePriorities(user.userId, request.priorityUpdates.map { it.claimId to it.priority })
        }

        return ResponseEntity.ok().build()
    }

}

