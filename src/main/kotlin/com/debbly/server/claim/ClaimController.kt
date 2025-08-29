package com.debbly.server.claim

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.category.model.CategoryModel
import com.debbly.server.claim.GetTopClaimsResponse.UserClaimResponse
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.TagModel
import com.debbly.server.claim.user.UserClaimService
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/claims")
class ClaimController(
    private val claimService: ClaimService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService
) {

    @GetMapping("/top")
    fun getTopClaims(
        @ExternalUserId externalUserId: String?
    ): List<GetTopClaimsResponse> {
        val userId = externalUserId?.let { authService.authenticate(it).userId }

        val claimIdToUserClaim = userId?.let {
            userClaimService.getUserClaims(it).associateBy { userClaim -> userClaim.claim.claimId }
        }
            ?: emptyMap()

        return claimService.findAll().map { claim ->
            val userClaim = claimIdToUserClaim[claim.claimId]
            GetTopClaimsResponse(
                claimId = claim.claimId,
                category = claim.category,
                title = claim.title,
                tags = claim.tags,
                userClaim = userClaim?.let {
                    UserClaimResponse(
                        stance = it.stance,
                    )
                }
            )
        }

    }

//    fun getTopClaims(
//        @RequestParam(defaultValue = "100") limit: Int
//    ): List<ClaimModel> {
//        return claimService.getTopClaims(limit)
//    }

    @PostMapping("/propose")
    fun proposeClaim(
        @RequestBody request: ProposeClaimRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<ClaimModel> {
        val claim = authService.authenticate(externalUserId).let { user ->
            claimService.propose(request.title, user.userId, request.stance)
        }

        return ResponseEntity.ok(claim)
    }

    data class ProposeClaimRequest(
        @field:Size(max = 255, message = "Title must be at most 255 characters long")
        val title: String,
        val stance: ClaimStance
    )
}

data class GetTopClaimsResponse(
    val claimId: String,
    val category: CategoryModel,
    val title: String,
    val tags: List<TagModel>,
    val userClaim: UserClaimResponse?
) {
    data class UserClaimResponse(
        val stance: ClaimStance,
    )
}