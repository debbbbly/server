package com.debbly.server.claim.model

import com.debbly.server.category.model.CategoryModel
import com.debbly.server.category.model.toEntity
import com.debbly.server.category.model.toModel
import com.debbly.server.claim.repository.ClaimEntity
import com.debbly.server.claim.tag.TagEntity
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class ClaimModel(
    val claimId: String,
    val category: CategoryModel,
    val title: String,
    val tags: List<TagModel>,
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

fun ClaimStance.opposite(): ClaimStance = when (this) {
    ClaimStance.FOR -> ClaimStance.AGAINST
    ClaimStance.AGAINST -> ClaimStance.FOR
    ClaimStance.EITHER -> ClaimStance.EITHER
}

data class TagModel(
    val tagId: String,
    val title: String
)

fun ClaimEntity.toModel(): ClaimModel = ClaimModel(
    claimId = claimId,
    category = category.toModel(),
    title = title,
    tags = tags.map { it.toModel() }.toList(),
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
    category = category.toEntity(),
    title = title,
    tags = tags.map { it.toEntity() }.toSet(),
    popularity = popularity,
    createdAt = createdAt,
    scoreFreshness = scoreFreshness,
    scoreStancesRecent = scoreStancesRecent,
    scoreDebatesRecent = scoreDebatesRecent,
    scoreBaseline = scoreBaseline,
    scoreTotal = scoreTotal
)

fun TagEntity.toModel(): TagModel = TagModel(
    tagId = tagId,
    title = title
)

fun TagModel.toEntity(): TagEntity = TagEntity(
    tagId = tagId,
    title = title
)

data class UserClaimModel(
    val claim: ClaimModel,
    val userId: String,
    val stance: ClaimStance,
    val priority: Int?,
    val updatedAt: Instant
)
