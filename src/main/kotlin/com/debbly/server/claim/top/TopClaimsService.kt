package com.debbly.server.claim.top

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.toModel
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.user.repository.UserClaimJpaRepository
import com.debbly.server.category.repository.CategoryCachedRepository
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.user.repository.UserJpaRepository
import org.slf4j.LoggerFactory.getLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Service
class TopClaimsService(
    private val claimJpaRepository: ClaimJpaRepository,
    private val stageJpaRepository: StageJpaRepository,
    private val userClaimJpaRepository: UserClaimJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val topClaimRedisRepository: TopClaimRedisRepository,
    private val categoryCachedRepository: CategoryCachedRepository,
    private val clock: Clock
) {
    private val logger = getLogger(javaClass)

    fun getTopClaimsFromCache(): List<TopClaimResponse> {
        return topClaimRedisRepository.findAllByOrderByRankAsc().map { stats ->
            TopClaimResponse(
                claimId = stats.claimId,
                categoryId = stats.categoryId,
                topicId = stats.topicId,
                title = stats.title,
                rank = stats.rank,
                recentDebates = stats.recentDebates,
                forCount = stats.forCount,
                againstCount = stats.againstCount,
                recentInterest = stats.recentInterest
            )
        }
    }

    fun getTopClaimsForTopic(topicId: String, limit: Int = 10): List<TopClaimWithStats> {
        return topClaimRedisRepository.findAllByOrderByRankAsc()
            .filter { it.topicId == topicId }
            .take(limit)
    }

    /**
     * Calculate and update top claims based on recent activity and ranking algorithm.
     */
    @Transactional(readOnly = true)
    fun calculateAndUpdateTopClaims() {
        val now = Instant.now(clock)
        val last14Days = now.minus(Duration.ofDays(14))
        val last48Hours = now.minus(Duration.ofHours(48))
        val last45Minutes = now.minus(Duration.ofMinutes(45))
        val last3Days = now.minus(Duration.ofDays(3))

        val activeCategoryIds = categoryCachedRepository.findAll()
            .filter { it.active }
            .map { it.categoryId }
            .toSet()

        val recentClaims = claimJpaRepository.findByCreatedAtAfter(last14Days)
            .filter { it.categoryId in activeCategoryIds }
            .associate { it.claimId to it.toModel() }

        val latestClaimsPerCategory = activeCategoryIds.flatMap { categoryId ->
            claimJpaRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId, 10)
        }.associate { it.claimId to it.toModel() }

        val allRecentClaims = (recentClaims + latestClaimsPerCategory)

        val claimIdsFromDebates = stageJpaRepository.findClaimIdsByOpenedAtAfter(last3Days).toSet()

        val activeUserIds48h = userJpaRepository.findUserIdsByLastSeenAfter(last48Hours)
        val activeUserIds30min = userJpaRepository.findUserIdsByLastSeenAfter(last45Minutes)

        val claimIdsFromActiveUserStances = if (activeUserIds48h.isNotEmpty()) {
            userClaimJpaRepository.findClaimIdsByUserIds(activeUserIds48h).toSet()
        } else {
            emptySet()
        }

        val allRelevantClaimIds = (allRecentClaims.keys + claimIdsFromDebates + claimIdsFromActiveUserStances).distinct()

        // logger.info("Found ${allRelevantClaimIds.size} relevant claims to rank")

        if (allRelevantClaimIds.isEmpty()) {
            logger.warn("No relevant claims found, top claims will not be updated")
            return
        }

        val claimIdToDebateCount = stageJpaRepository.countRecentDebatesByClaimId(last48Hours)
            .associate { it.getClaimId() to it.getCount().toInt() }

        val claimIdToInterest30minCount = if (activeUserIds30min.isNotEmpty()) {
            userClaimJpaRepository.findClaimIdsByUserIds(activeUserIds30min)
                .groupBy { it }
                .mapValues { it.value.size }
        } else {
            emptyMap()
        }

        val stanceCounts = userClaimJpaRepository.countStancesByClaimIds(allRelevantClaimIds)

        val forCountsMap = mutableMapOf<String, Int>()
        val againstCountsMap = mutableMapOf<String, Int>()

        stanceCounts.forEach { breakdown ->
            val claimId = breakdown.getClaimId()
            val stance = ClaimStance.valueOf(breakdown.getStance())
            val count = breakdown.getCount().toInt()

            when (stance) {
                ClaimStance.FOR -> forCountsMap[claimId] = count
                ClaimStance.AGAINST -> againstCountsMap[claimId] = count
                ClaimStance.EITHER -> {}
            }
        }

        val allClaimsMap = claimJpaRepository.findByClaimIds(allRelevantClaimIds)
            .filter { it.categoryId in activeCategoryIds }
            .associate { it.claimId to it.toModel() }

        val scoredClaims = allRelevantClaimIds.mapNotNull { claimId ->
            val claim = allRecentClaims[claimId] ?: allClaimsMap[claimId]
            if (claim == null || claim.categoryId !in activeCategoryIds) {
                return@mapNotNull null
            }

            val debateCount = claimIdToDebateCount[claimId] ?: 0
            val i30Count = claimIdToInterest30minCount[claimId] ?: 0
            val forCount = forCountsMap[claimId] ?: 0
            val againstCount = againstCountsMap[claimId] ?: 0

            val ageHours = Duration.between(claim.createdAt, now).toHours().toDouble()

            // Calculate balance factor (1.0 = perfectly balanced, 0.0 = one-sided)
            // Clamp min to 0.3 so it never fully dies
            val balance = max(0.3, 1.0 - abs(forCount - againstCount).toDouble() / max(forCount + againstCount, 1))

            // Calculate freshness boost (new claims get boost for first ~24h)
            val freshness = min(1.0, 24.0 / max(ageHours, 1.0))

            // Final score: (2.0 * D) + (3.0 * I30) × balance × freshness
            val score = (2.0 * debateCount + 3.0 * i30Count) * balance * freshness

            ClaimScore(
                claimId = claimId,
                score = score,
                debatesIn48h = debateCount,
                forCount = forCount,
                againstCount = againstCount,
                addedAt = claim.createdAt
            )
        }

        val topClaims = scoredClaims
            .sortedByDescending { it.score }
            .take(50)

        // logger.info("Top claims calculated: ${topClaims.size} claims")

        topClaimRedisRepository.deleteAll()

        topClaims.forEachIndexed { index, claimScore ->
            val claim = allRecentClaims[claimScore.claimId] ?: allClaimsMap[claimScore.claimId]
            if (claim != null) {
                topClaimRedisRepository.save(
                    TopClaimWithStats(
                        claimId = claimScore.claimId,
                        categoryId = claim.categoryId,
                        topicId = claim.topicId,
                        title = claim.title,
                        rank = index + 1,
                        score = claimScore.score,
                        recentDebates = claimScore.debatesIn48h,
                        forCount = claimScore.forCount,
                        againstCount = claimScore.againstCount,
                        recentInterest = claimIdToInterest30minCount[claimScore.claimId] ?: 0
                    )
                )
            }
        }
    }

    private data class ClaimScore(
        val claimId: String,
        val score: Double,
        val debatesIn48h: Int,
        val forCount: Int,
        val againstCount: Int,
        val addedAt: Instant
    )


}
