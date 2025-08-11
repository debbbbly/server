package com.debbly.server.claim

data class StanceRequest(
    val claims: List<ClaimStanceUpdate>
)

data class ClaimStanceUpdate(
    val claimId: String?,
    val title: String?,
    val stance: ClaimStance
)
