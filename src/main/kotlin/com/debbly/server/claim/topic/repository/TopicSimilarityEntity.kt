package com.debbly.server.claim.topic.repository

import jakarta.persistence.*
import java.time.Instant

@Entity(name = "topic_similarities")
@IdClass(TopicSimilarityId::class)
data class TopicSimilarityEntity(
    @Id
    @Column(name = "topic_id_1")
    val topicId1: String,

    @Id
    @Column(name = "topic_id_2")
    val topicId2: String,

    val similarity: Double,
    val createdAt: Instant
)

data class TopicSimilarityId(
    val topicId1: String = "",
    val topicId2: String = ""
) : java.io.Serializable
