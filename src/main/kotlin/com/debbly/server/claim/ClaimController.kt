package com.debbly.server.claim

import com.debbly.server.auth.ExternalUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/claims")
class ClaimController(
    private val service: ClaimService,
    private val stanceService: UserClaimStanceService
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

    @PostMapping("/stance")
    fun postStance(
        @RequestBody stanceRequest: StanceRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        if (externalUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        stanceService.processStances(stanceRequest.claims, externalUserId)

        return ResponseEntity.ok().build()
    }
}
