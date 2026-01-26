package com.debbly.server.claim.model

import com.debbly.server.claim.repository.ClaimEntity
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class",
)
data class ClaimModel(
    val claimId: String,
    val categoryId: String,
    val title: String,
    val slug: String?,
    val createdAt: Instant,
    val topicId: String,
    val stanceToTopic: StanceToTopic,
)

enum class ClaimStance {
    FOR,
    EITHER,
    AGAINST,
}

enum class StanceToTopic {
    FOR,
    NEUTRAL,
    AGAINST,
}

fun ClaimStance.opposite(): ClaimStance =
    when (this) {
        ClaimStance.FOR -> ClaimStance.AGAINST
        ClaimStance.AGAINST -> ClaimStance.FOR
        ClaimStance.EITHER -> ClaimStance.EITHER
    }

fun ClaimEntity.toModel(): ClaimModel =
    ClaimModel(
        claimId = claimId,
        categoryId = categoryId,
        title = title,
        slug = slug,
        createdAt = createdAt,
        topicId = topicId,
        stanceToTopic = stanceToTopic,
    )

fun ClaimModel.toEntity(): ClaimEntity =
    ClaimEntity(
        claimId = claimId,
        categoryId = categoryId,
        title = title,
        slug = slug,
        createdAt = createdAt,
        topicId = topicId,
        stanceToTopic = stanceToTopic,
    )

data class UserClaimModel(
    val claim: ClaimModel,
    val userId: String,
    val stance: ClaimStance,
    val priority: Int?,
    val updatedAt: Instant,
)
