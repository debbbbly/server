package com.debbly.server.stage.model

import com.debbly.server.claim.ClaimStance
import java.time.Instant

//@JsonTypeInfo(
//    use = JsonTypeInfo.Id.CLASS,
//    include = JsonTypeInfo.As.PROPERTY,
//    property = "@class",
//    defaultImpl = StageModel::class
//)
data class StageModel(
    val stageId: String,
    val type: StageType,
    val title: String?,
    val claimId: String?,
    val hosts: List<StageHostModel>,
    val createdAt: Instant,
    val closedAt: Instant? = null
) {
    data class StageHostModel(
        val userId: String,
        val stance: ClaimStance?
    )
}

enum class StageType {
    SOLO,
    ONE_ON_ONE
}