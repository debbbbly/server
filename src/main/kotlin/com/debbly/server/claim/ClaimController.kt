package com.debbly.server.claim

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.claim.GetTopClaimsResponse.UserClaimResponse
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.top.TopClaimsService
import com.debbly.server.claim.user.UserClaimService
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/claims")
class ClaimController(
    private val claimService: ClaimService,
    private val claimCachedRepository: ClaimCachedRepository,
    private val topClaimsService: TopClaimsService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService,
    private val claimSimilarityService: ClaimSimilarityService
) {

    @GetMapping("/top")
    fun getTopClaims(
        @ExternalUserId externalUserId: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
        @RequestParam(required = false) categoryIds: List<String>?
    ): List<GetTopClaimsResponse> {
        val userId = externalUserId?.let { authService.authenticate(it).userId }

        val claimIdToUserClaim = userId?.let {
            userClaimService.getClaims(it).associateBy { userClaim -> userClaim.claim.claimId }
        }
            ?: emptyMap()

        val topClaims = topClaimsService.getTopClaimsFromCache()
            .let { claims ->
                if (categoryIds.isNullOrEmpty()) {
                    claims
                } else {
                    claims.filter { it.categoryId in categoryIds }
                }
            }
            .take(limit)
        val claimIds = topClaims.map { it.claimId }.toSet()
        val claimsMap = claimCachedRepository.findAll()
            .filter { it.claimId in claimIds }
            .associateBy { it.claimId }

        return topClaims.mapNotNull { topClaim ->
            val claim = claimsMap[topClaim.claimId] ?: return@mapNotNull null
            val userClaim = claimIdToUserClaim[topClaim.claimId]

            GetTopClaimsResponse(
                claimId = topClaim.claimId,
                categoryId = claim.categoryId,
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

    @PostMapping("/create")
    fun create(
        @RequestBody request: CreateClaimRequest,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<ClaimModel> {
        val user = authService.authenticate(externalUserId)

        if (!ClaimRateLimiter.tryConsume(user.userId)) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. You can create up to 10 claims per day."
            )
        }

        val claim = claimService.create(request.title, user.userId, request.stance)

        return ResponseEntity.ok(claim)
    }

    @GetMapping("/similar")
    fun findSimilarClaims(
        @RequestParam
        @Size(max = 125, message = "Claim must be at most 125 characters long")
        title: String,
        @RequestParam(required = false)
        limit: Int?
    ): ResponseEntity<FindSimilarClaimsResponse> {
        val similarClaims = claimSimilarityService.findSimilarClaims(
            text = title,
            limit = limit ?: 5
        )

        return ResponseEntity.ok(
            FindSimilarClaimsResponse(
                similarClaims = similarClaims,
                hasDuplicates = similarClaims.any { it.isDuplicate }
            )
        )
    }

    data class CreateClaimRequest(
        @field:Size(max = 125, message = "Claim must be at most 125 characters long")
        val title: String,
        val stance: ClaimStance
    )

    data class FindSimilarClaimsResponse(
        val similarClaims: List<SimilarClaim>,
        val hasDuplicates: Boolean
    )
}

data class GetTopClaimsResponse(
    val claimId: String,
    val categoryId: String,
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
