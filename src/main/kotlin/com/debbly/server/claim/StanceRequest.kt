package com.debbly.server.claim

import com.debbly.server.claim.model.ClaimStance

data class StanceRequest(
    val claims: List<ClaimStanceUpdate>
)

data class ClaimStanceUpdate(
    val claimId: String?,
    val title: String?,
    val stance: ClaimStance
)
