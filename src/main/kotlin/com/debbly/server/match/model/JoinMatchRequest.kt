package com.debbly.server.match.model

import com.debbly.server.claim.model.ClaimStance

data class JoinMatchRequest(
    val claims: List<ClaimWithStance>?,
    val topics: List<TopicWithStance>?
) {
    init {
        require(!claims.isNullOrEmpty() || !topics.isNullOrEmpty()) {
            "At least one of claims or topics must be provided"
        }
    }
}

data class ClaimWithStance(
    val claimId: String,
    val stance: ClaimStance
)

data class TopicWithStance(
    val topicId: String,
    val stance: ClaimStance
)
