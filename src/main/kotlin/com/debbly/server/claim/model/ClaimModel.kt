package com.debbly.server.claim.model

import com.debbly.server.category.model.CategoryModel
import com.debbly.server.category.model.toEntity
import com.debbly.server.category.model.toModel
import com.debbly.server.claim.repository.ClaimEntity
import com.debbly.server.claim.tag.TagEntity
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class ClaimModel(
    val claimId: String,
    val category: CategoryModel,
    val title: String,
    val tags: Set<TagModel>
)

data class TagModel(
    val tagId: String,
    val title: String
)

fun ClaimEntity.toModel(): ClaimModel = ClaimModel(
    claimId = claimId,
    category = category.toModel(),
    title = title,
    tags = tags.map { it.toModel() }.toSet()
)

fun ClaimModel.toEntity(): ClaimEntity = ClaimEntity(
    claimId = claimId,
    category = category.toEntity(),
    title = title,
    tags = tags.map { it.toEntity() }.toSet()
)

fun TagEntity.toModel(): TagModel = TagModel(
    tagId = tagId,
    title = title
)

fun TagModel.toEntity(): TagEntity = TagEntity(
    tagId = tagId,
    title = title
)