package com.debbly.server.claim

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.category.model.CategoryModel
import com.debbly.server.claim.GetTopClaimsResponse.UserClaimResponse
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.TagModel
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.top.TopClaimsService
import com.debbly.server.claim.user.UserClaimService
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/claims")
class ClaimController(
    private val claimService: ClaimService,
    private val claimCachedRepository: ClaimCachedRepository,
    private val topClaimsService: TopClaimsService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService
) {

    @GetMapping("/top")
    fun getTopClaims(
        @ExternalUserId externalUserId: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): List<GetTopClaimsResponse> {
        val userId = externalUserId?.let { authService.authenticate(it).userId }

        val claimIdToUserClaim = userId?.let {
            userClaimService.getClaims(it).associateBy { userClaim -> userClaim.claim.claimId }
        }
            ?: emptyMap()

        val topClaims = topClaimsService.getTopClaimsFromCache().take(limit)
        val claimIds = topClaims.map { it.claimId }.toSet()
        val claimsMap = claimCachedRepository.findAll()
            .filter { it.claimId in claimIds }
            .associateBy { it.claimId }

        return topClaims.mapNotNull { topClaim ->
            val claim = claimsMap[topClaim.claimId] ?: return@mapNotNull null
            val userClaim = claimIdToUserClaim[topClaim.claimId]

            GetTopClaimsResponse(
                claimId = topClaim.claimId,
                category = claim.category,
                title = topClaim.title,
                rank = topClaim.rank,
                recentDebates = topClaim.recentDebates,
                forCount = topClaim.forCount,
                againstCount = topClaim.againstCount,
                recentInterest = topClaim.recentInterest,
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
        @field:Size(max = 125, message = "Claim must be at most 125 characters long")
        val title: String,
        val stance: ClaimStance
    )
}

data class GetTopClaimsResponse(
    val claimId: String,
    val category: CategoryModel,
    val title: String,
    val rank: Int,
    val recentDebates: Int,
    val forCount: Int,
    val againstCount: Int,
    val recentInterest: Int,
    val userClaim: UserClaimResponse?
) {
    data class UserClaimResponse(
        val stance: ClaimStance,
    )
}