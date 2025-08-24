package com.debbly.server.claim

import com.debbly.server.claim.model.ClaimSide

data class ClaimSideUpdateRequest(
    val claims: List<ClaimSideUpdate>
)

data class ClaimSideUpdate(
    val claimId: String?,
    val title: String?,
    val side: ClaimSide
)
