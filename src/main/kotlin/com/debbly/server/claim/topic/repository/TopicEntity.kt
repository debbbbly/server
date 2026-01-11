package com.debbly.server.claim.topic.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.Instant

@Entity(name = "topics")
data class TopicEntity(
    @Id
    val topicId: String,
    @Column(name = "category_id")
    val categoryId: String,
    val title: String,
    val createdAt: Instant,
    var updatedAt: Instant
)