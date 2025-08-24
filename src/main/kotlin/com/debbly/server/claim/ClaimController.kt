package com.debbly.server.claim

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import org.springframework.http.HttpStatus
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
    fun getAllClaims(): List<ClaimEntity> = service.findAll()

    @GetMapping("/top")
    fun getOptions(
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(defaultValue = "5") limit: Int
    ): List<ClaimEntity> {
        return service.getTopClaims(categoryIds, limit)
    }

    @PostMapping
    fun createClaim(
        @RequestBody claim: ClaimEntity,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {

        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        return ResponseEntity.ok().build()
    }

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
