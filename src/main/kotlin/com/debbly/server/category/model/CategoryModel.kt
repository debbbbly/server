package com.debbly.server.category.model

import com.debbly.server.category.CategoryEntity

data class CategoryModel(
    val categoryId: String,
    val title: String,
    val description: String?,
    val avatarUrl: String,
    val active: Boolean = true
)

fun CategoryEntity.toModel(): CategoryModel = CategoryModel(
    categoryId = categoryId,
    title = title,
    description = description,
    avatarUrl = avatarUrl,
    active = active
)

fun CategoryModel.toEntity(): CategoryEntity = CategoryEntity(
    categoryId = categoryId,
    title = title,
    description = description,
    avatarUrl = avatarUrl,
    active = active
)