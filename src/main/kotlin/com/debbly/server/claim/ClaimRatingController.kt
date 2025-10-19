package com.debbly.server.claim

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Controller for managing claim rating calculations.
 * Provides endpoints to trigger rating recalculations for all claims or individual claims.
 */
@RestController
@RequestMapping("/api/public/claims/ratings")
class ClaimRatingController(
    private val claimRatingService: ClaimRatingService
) {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    /**
     * Trigger rating recalculation for all claims in the database.
     */
    @PostMapping("/recalculate-all")
    fun recalculateAllClaimRatings(): ResponseEntity<Void> {
        logger.info("Received request to recalculate all claim ratings")
        
        return try {
            claimRatingService.updateAllClaimRatings()

            ResponseEntity.ok().build()
            
        } catch (e: Exception) {
            logger.error("Error recalculating claim ratings", e)
            ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * Calculate rating for a specific claim by ID.
     * Useful when a new claim is created or when you want to update a single claim's rating.
     */
    @PostMapping("/{claimId}/calculate")
    fun calculateClaimRating(@PathVariable claimId: String): ResponseEntity<Void> {
        logger.info("Received request to calculate rating for claim: {}", claimId)

        claimRatingService.calculateClaimRating(claimId)

        return ResponseEntity.ok().build()
    }
    

}