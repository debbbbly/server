package com.debbly.server.claim

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.claim.GetTopClaimsResponse.UserClaimResponse
import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.top.TopClaimsService
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.user.UserClaimService
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.user.repository.UserCachedRepository
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@RestController
@RequestMapping("/claims")
class ClaimController(
    private val claimService: ClaimService,
    private val claimCachedRepository: ClaimCachedRepository,
    private val topClaimsService: TopClaimsService,
    private val userClaimService: UserClaimService,
    private val authService: AuthService,
    private val claimSimilarityService: ClaimSimilarityService,
    private val stageJpaRepository: StageJpaRepository,
    private val userCachedRepository: UserCachedRepository,
    private val topicRepository: TopicRepository
) {

    @GetMapping("/top")
    fun getTopClaims(
        @ExternalUserId externalUserId: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
        @RequestParam(required = false) categoryIds: List<String>?,
        @RequestParam(required = false) topicIds: List<String>?
    ): List<GetTopClaimsResponse> {
        val userId = externalUserId?.let { authService.authenticate(it).userId }

        val claimIdToUserClaim = userId?.let {
            userClaimService.getClaims(it).associateBy { userClaim -> userClaim.claim.claimId }
        }
            ?: emptyMap()

        val topClaims = topClaimsService.getTopClaimsFromCache()
            .filter { claim ->
                val matchesCategory = categoryIds.isNullOrEmpty() || claim.categoryId in categoryIds
                val matchesTopic = topicIds.isNullOrEmpty() || claim.topicId in topicIds
                matchesCategory && matchesTopic
            }
            .take(limit)

        return topClaims.mapNotNull { topClaim ->
            val userClaim = claimIdToUserClaim[topClaim.claimId]

            GetTopClaimsResponse(
                claimId = topClaim.claimId,
                claimSlug = topClaim.claimSlug,
                categoryId = topClaim.categoryId,
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

    /**
     * Get claim detail. Accepts either claimId or claimSlug.
     */
    @GetMapping("/{claimIdOrSlug}")
    fun getClaimById(
        @PathVariable claimIdOrSlug: String,
        @ExternalUserId externalUserId: String?,
        @RequestParam(defaultValue = "10") stageLimit: Int
    ): ResponseEntity<ClaimDetailResponse> {
        val claim = claimCachedRepository.findById(claimIdOrSlug)
            ?: claimCachedRepository.findBySlug(claimIdOrSlug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found")

        val userId = externalUserId?.let { authService.authenticate(it).userId }

        // Fetch topic to get its slug
        val topic = topicRepository.findById(claim.topicId).getOrNull()
        val topicSlug = topic?.slug ?: claim.topicId

        // Try to get stats from top claims cache
        val topClaimStats = topClaimsService.getTopClaimsFromCache()
            .find { it.claimId == claim.claimId }

        val userClaim = userId?.let {
            userClaimService.getClaims(it).find { uc -> uc.claim.claimId == claim.claimId }
        }

        // Fetch stages for this claim
        val stages = stageJpaRepository.findStagesByClaimId(
            claimId = claim.claimId,
            statuses = listOf(StageStatus.OPEN, StageStatus.RECORDED)
        ).take(stageLimit.coerceIn(1, 50))

        val userIds = stages.flatMap { it.hosts.map { host -> host.id.userId } }.distinct()
        val usersMap = userCachedRepository.findByIds(userIds)

        val stageResponses = stages.map { stage ->
            ClaimDetailResponse.StageResponse(
                stageId = stage.stageId,
                hosts = stage.hosts.map { host ->
                    val user = usersMap[host.id.userId]
                    ClaimDetailResponse.HostResponse(
                        userId = host.id.userId,
                        username = user?.username ?: "unknown",
                        avatarUrl = user?.avatarUrl,
                        stance = host.stance
                    )
                },
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt
            )
        }

        return ResponseEntity.ok(
            ClaimDetailResponse(
                claimId = claim.claimId,
                claimSlug = claim.slug,
                categoryId = claim.categoryId,
                topicId = claim.topicId,
                topicSlug = topicSlug,
                title = claim.title,
                recentDebates = topClaimStats?.recentDebates ?: 0,
                forCount = topClaimStats?.forCount ?: 0,
                againstCount = topClaimStats?.againstCount ?: 0,
                recentInterest = topClaimStats?.recentInterest ?: 0,
                userClaim = userClaim?.let {
                    ClaimDetailResponse.UserClaimResponse(stance = it.stance)
                },
                stages = stageResponses
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
    val claimSlug: String?,
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

data class ClaimDetailResponse(
    val claimId: String,
    val claimSlug: String?,
    val categoryId: String,
    val topicId: String,
    val topicSlug: String,
    val title: String,
    val recentDebates: Int,
    val forCount: Int,
    val againstCount: Int,
    val recentInterest: Int,
    val userClaim: UserClaimResponse?,
    val stages: List<StageResponse>
) {
    data class UserClaimResponse(
        val stance: ClaimStance
    )

    data class StageResponse(
        val stageId: String,
        val hosts: List<HostResponse>,
        val status: StageStatus,
        val openedAt: Instant?,
        val closedAt: Instant?
    )

    data class HostResponse(
        val userId: String,
        val username: String,
        val avatarUrl: String?,
        val stance: ClaimStance?
    )
}
