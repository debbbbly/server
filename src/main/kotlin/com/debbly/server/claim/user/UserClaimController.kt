package com.debbly.server.claim.user

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.claim.ClaimService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.user.UserClaimController.GetUserClaimsResponse.GetUserClaimsResponse
import com.debbly.server.infra.error.UnauthorizedException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/claims")
class UserClaimController(
    private val service: ClaimService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService
) {

    @GetMapping
    fun getUserClaims(
        @RequestParam(defaultValue = "50") limit: Int,
        @PathVariable userId: String,
    ): List<GetUserClaimsResponse> {
        return userClaimService.getClaims(userId).map { userClaim ->
            GetUserClaimsResponse(
                claimId = userClaim.claim.claimId,
                claimSlug = userClaim.claim.slug,
                categoryId = userClaim.claim.categoryId,
                title = userClaim.claim.title,
                userClaim = GetUserClaimsResponse(
                    stance = userClaim.stance,
                )
            )
        }
    }

    @PostMapping("/{claimId}/update-stance")
    fun updateUserClaimStance(
        @PathVariable userId: String,
        @PathVariable claimId: String,
        @RequestBody request: UpdateUserClaimStance,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            if (user.userId != userId)
                throw UnauthorizedException("Can't update claim stance of a different user.")

            userClaimService.updateStance(user.userId, claimId, request.stance)
        }

        return ResponseEntity.ok().build()
    }

    data class UpdateUserClaimStance(
        val stance: ClaimStance?
    )

//    @PostMapping("/{claimId}/remove-stance")
//    fun removeUserClaimStance(
//        @PathVariable userId: String,
//        @PathVariable claimId: String,
//        @ExternalUserId externalUserId: String?
//    ): ResponseEntity<Void> {
//        authService.authenticate(externalUserId).let { user ->
//            if (user.userId != userId)
//                throw UnauthorizedException("Can't update claim stance of a different user.")
//
//            userClaimService.removeStance(user.userId, claimId)
//        }
//
//        return ResponseEntity.ok().build()
//    }

//    data class PriorityUpdate(
//        val claimId: String,
//        val priority: Int
//    )

//    data class UpdatePrioritiesRequest(
//        val priorityUpdates: List<PriorityUpdate>
//    )

//    @PostMapping("/priority")
//    fun updatePriorities(
//        @RequestBody request: UpdatePrioritiesRequest,
//        @ExternalUserId externalUserId: String?
//    ): ResponseEntity<Void> {
//        authService.authenticate(externalUserId).let { user ->
//            userClaimService.updatePriorities(user.userId, request.priorityUpdates.map { it.claimId to it.priority })
//        }
//
//        return ResponseEntity.ok().build()
//    }

    data class GetUserClaimsResponse(
        val claimId: String,
        val claimSlug: String?,
        val categoryId: String,
        val title: String,
        val userClaim: GetUserClaimsResponse
    ) {
        data class GetUserClaimsResponse(
            val stance: ClaimStance,
        )
    }
}
