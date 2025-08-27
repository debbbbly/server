package com.debbly.server.claim.user

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.claim.ClaimService
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/claims")
class UserClaimController(
    private val service: ClaimService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService
) {

    @GetMapping("/user")
    fun getUserClaims(
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(defaultValue = "5") limit: Int,
        @ExternalUserId externalUserId: String?
    ): List<UserClaimModel> {
        return authService.authenticate(externalUserId)
            .let { user -> service.getUserClaims(user.userId, categoryIds, limit) }
    }

    data class ProposeClaimRequest(
        @field:Size(max = 255, message = "Title must be at most 255 characters long")
        val title: String,
        val stance: ClaimStance
    )

    @PostMapping("/stance")
    fun postStance(
        @RequestBody stanceUpdateRequest: StanceUpdateRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            userClaimService.save(stanceUpdateRequest.claims, user)
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("/priority")
    fun updatePriorities(
        @RequestBody priorityUpdateRequest: PriorityUpdateRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            userClaimService.updatePriorities(priorityUpdateRequest.priorityUpdates, user)
        }

        return ResponseEntity.ok().build()
    }
}

data class StanceUpdateRequest(
    val claims: List<ClaimStanceUpdate>
)

data class ClaimStanceUpdate(
    val claimId: String?,
    val title: String?,
    val stance: ClaimStance
)

data class PriorityUpdate(
    val claimId: String,
    val priority: Int
)

data class PriorityUpdateRequest(
    val priorityUpdates: List<PriorityUpdate>
)
