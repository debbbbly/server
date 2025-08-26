package com.debbly.server.claim

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimSide
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/claims")
class ClaimController(
    private val service: ClaimService,
    private val sideService: UserClaimSideService,
    private val authService: AuthService
) {

    // TODO remove or auth
    @GetMapping
    fun getAllClaims(): List<ClaimModel> = service.findAll()

    @GetMapping("/top")
    fun getOptions(
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(defaultValue = "5") limit: Int
    ): List<ClaimModel> {
        return service.getTopClaims(categoryIds, limit)
    }

    @PostMapping("/propose")
    fun proposeClaim(
        @RequestBody request: ProposeClaimRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<ClaimModel> {
        val claim = authService.authenticate(externalUserId).let { user ->
            service.propose(request.title, user.userId, request.side)
        }

        return ResponseEntity.ok(claim)
    }

    data class ProposeClaimRequest(
        @field:Size(max = 255, message = "Title must be at most 255 characters long")
        val title: String,
        val side: ClaimSide
    )

    @PostMapping("/side")
    fun postSide(
        @RequestBody claimSideUpdateRequest: ClaimSideUpdateRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            sideService.save(claimSideUpdateRequest.claims, user)
        }

        return ResponseEntity.ok().build()
    }
}
