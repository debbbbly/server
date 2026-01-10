package com.debbly.server.claim.model

import com.debbly.server.claim.repository.ClaimEntity
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class ClaimModel(
    val claimId: String,
    val categoryId: String,
    val title: String,
    val popularity: Int?,
    val createdAt: Instant,
    val scoreFreshness: Double? = null,
    val scoreStancesRecent: Double? = null,
    val scoreDebatesRecent: Double? = null,
    val scoreBaseline: Double? = null,
    val scoreTotal: Double? = null
)

enum class ClaimStance {
    FOR,
    EITHER,
    AGAINST,
}

enum class TopicStance {
    FOR,
    EITHER,
    NEUTRAL,
}


fun ClaimStance.opposite(): ClaimStance = when (this) {
    ClaimStance.FOR -> ClaimStance.AGAINST
    ClaimStance.AGAINST -> ClaimStance.FOR
    ClaimStance.EITHER -> ClaimStance.EITHER
}

fun ClaimEntity.toModel(): ClaimModel = ClaimModel(
    claimId = claimId,
    categoryId = categoryId,
    title = title,
    popularity = popularity,
    createdAt = createdAt,
    scoreFreshness = scoreFreshness,
    scoreStancesRecent = scoreStancesRecent,
    scoreDebatesRecent = scoreDebatesRecent,
    scoreBaseline = scoreBaseline,
    scoreTotal = scoreTotal
)

fun ClaimModel.toEntity(): ClaimEntity = ClaimEntity(
    claimId = claimId,
    categoryId = categoryId,
    title = title,
    popularity = popularity,
    createdAt = createdAt,
    scoreFreshness = scoreFreshness,
    scoreStancesRecent = scoreStancesRecent,
    scoreDebatesRecent = scoreDebatesRecent,
    scoreBaseline = scoreBaseline,
    scoreTotal = scoreTotal
)

data class UserClaimModel(
    val claim: ClaimModel,
    val userId: String,
    val stance: ClaimStance,
    val priority: Int?,
    val updatedAt: Instant
)
