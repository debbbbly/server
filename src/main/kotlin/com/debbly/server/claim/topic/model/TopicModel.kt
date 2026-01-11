package com.debbly.server.claim.topic.model

import com.debbly.server.claim.topic.repository.TopicEntity
import java.time.Instant

data class TopicModel(
    val topicId: String,
    val categoryId: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

fun TopicEntity.toModel(): TopicModel = TopicModel(
    topicId = topicId,
    categoryId = categoryId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TopicModel.toEntity(): TopicEntity = TopicEntity(
    topicId = topicId,
    categoryId = categoryId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt
)
