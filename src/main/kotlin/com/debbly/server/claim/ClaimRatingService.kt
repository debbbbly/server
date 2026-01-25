package com.debbly.server.claim

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ClaimRatingService {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun updateAllClaimRatings() {
        logger.info("Claim rating calculations are disabled")
    }

    fun calculateClaimRating(claimId: String) {
        logger.info("Claim rating calculations are disabled for claim {}", claimId)
    }
}
