package com.debbly.server.claim

import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.user.repository.UserClaimJpaRepository
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.claim.model.ClaimModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.exp

/**
 * Service for calculating and updating claim scores based on freshness, recent activity,
 * and baseline metrics. Uses dynamic weights that adapt based on overall platform activity.
 */
@Service
class ClaimRatingService(
    private val claimRepository: ClaimCachedRepository,
    private val claimJpaRepository: ClaimJpaRepository,
    private val userClaimJpaRepository: UserClaimJpaRepository,
    private val stageJpaRepository: StageJpaRepository,
    private val clock: Clock
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    companion object {
        private const val DAYS_TO_CALCULATE = 3L
        
        // Fixed weights for now (early stage)
        private const val W_FRESH = 0.3
        private const val W_STANCE = 0.3
        private const val W_DEBATE = 0.3
        private const val W_BASE = 0.1
    }

    /**
     * Calculate and update scores for all claims in the database.
     */
    @Transactional
    fun updateAllClaimRatings() {
        logger.info("Starting claim rating calculation for all claims")

        val since = Instant.now(clock).minusSeconds(DAYS_TO_CALCULATE * 24 * 60 * 60)
        val factors = calculateNormalizationFactors(since)

        claimRepository.findAll().forEach { claim ->
            calculateAndUpdateClaimScore(claim, factors)
        }

    }
    
    /**
     * Calculate and update the score for a single claim.
     */
    fun calculateClaimRating(claimId: String){
        val since = Instant.now(clock).minusSeconds(DAYS_TO_CALCULATE * 24 * 60 * 60)
        val factors = calculateNormalizationFactors(since)

        calculateAndUpdateClaimScore(claimRepository.getById(claimId), factors)
    }
    
    /**
     * Calculate normalization factors for all metrics across all claims.
     */
    private fun calculateNormalizationFactors(since: Instant): NormalizationFactors {
        val debatesCountsRaw = stageJpaRepository.countRecentDebatesByClaimId(since)
        val stancesCountsRaw = userClaimJpaRepository.countRecentStancesByClaimId(since)
        val uniqueDebatersCountsRaw = stageJpaRepository.countUniqueDebatersByClaimId()

        // Convert List<Array<Any>> to Map<String, Long>
        val debatesCounts = debatesCountsRaw.associate { row: Array<Any> -> 
            (row[0] as String) to (row[1] as Number).toLong() 
        }
        val stancesCounts = stancesCountsRaw.associate { row: Array<Any> -> 
            (row[0] as String) to (row[1] as Number).toLong() 
        }
        val uniqueDebatersCounts = uniqueDebatersCountsRaw.associate { row: Array<Any> -> 
            (row[0] as String) to (row[1] as Number).toLong() 
        }

        val debatesMax = debatesCounts.values.maxOfOrNull { it }?.toDouble() ?: 1.0
        val stancesMax = stancesCounts.values.maxOfOrNull { it }?.toDouble() ?: 1.0
        val uniqueDebatersMax = uniqueDebatersCounts.values.maxOfOrNull { it }?.toDouble() ?: 1.0
        
        return NormalizationFactors(
            stancesMax = stancesMax,
            debatesMax = debatesMax,
            uniqueDebatersMax = uniqueDebatersMax,
            stancesCounts = stancesCounts,
            debatesCounts = debatesCounts,
            uniqueDebatersCounts = uniqueDebatersCounts
        )
    }
    
    /**
     * Calculate and update the score for a single claim.
     */
    private fun calculateAndUpdateClaimScore(
        claim: ClaimModel, 
        factors: NormalizationFactors
    ) {
        // Calculate individual rating components
        val freshnessScore = calculateFreshnessNorm(claim.createdAt)
        val stancesScore = calculateStancesRecentNorm(claim.claimId, factors)
        val debatesScore = calculateDebatesRecentNorm(claim.claimId, factors)
        val baselineScore = calculateBaselineNorm(claim.claimId, factors)
        
        // Calculate final weighted score
        val totalScore = W_FRESH * freshnessScore +
                        W_STANCE * stancesScore +
                        W_DEBATE * debatesScore +
                        W_BASE * baselineScore
        
        val updatedClaim = claim.copy(
            scoreFreshness = freshnessScore,
            scoreStancesRecent = stancesScore,
            scoreDebatesRecent = debatesScore,
            scoreBaseline = baselineScore,
            scoreTotal = totalScore
        )
        
        claimRepository.save(updatedClaim)
    }
    
    /**
     * Calculate freshness normalization based on claim age.
     * Uses exponential decay: exp(-age_days / 14.0)
     */
    private fun calculateFreshnessNorm(createdAt: Instant): Double {
        val ageDays = Duration.between(createdAt, Instant.now(clock)).toDays()
        return exp(-ageDays.toDouble() / 14.0)
    }
    
    /**
     * Calculate normalized recent stances score.
     */
    private fun calculateStancesRecentNorm(claimId: String, factors: NormalizationFactors): Double {
        val recentStances = factors.stancesCounts[claimId] ?: 0L
        return if (factors.stancesMax > 0) recentStances.toDouble() / factors.stancesMax else 0.0
    }
    
    /**
     * Calculate normalized recent debates score.
     */
    private fun calculateDebatesRecentNorm(claimId: String, factors: NormalizationFactors): Double {
        val recentDebates = factors.debatesCounts[claimId] ?: 0L
        return if (factors.debatesMax > 0) recentDebates.toDouble() / factors.debatesMax else 0.0
    }
    
    /**
     * Calculate normalized baseline score based on total unique debaters.
     */
    private fun calculateBaselineNorm(claimId: String, factors: NormalizationFactors): Double {
        val uniqueDebaters = factors.uniqueDebatersCounts[claimId] ?: 0L
        return if (factors.uniqueDebatersMax > 0) uniqueDebaters.toDouble() / factors.uniqueDebatersMax else 0.0
    }
    
    /**
     * Data class for normalization factors and cached counts.
     */
    private data class NormalizationFactors(
        val stancesMax: Double,
        val debatesMax: Double,
        val uniqueDebatersMax: Double,
        val stancesCounts: Map<String, Long>,
        val debatesCounts: Map<String, Long>,
        val uniqueDebatersCounts: Map<String, Long>
    )
}