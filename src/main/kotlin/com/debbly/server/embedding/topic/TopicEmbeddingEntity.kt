package com.debbly.server.embedding.topic

import java.time.Instant

data class TopicEmbeddingEntity(
    val topicId: String,
    val categoryId: String,
    val title: String,
    val embedding: FloatArray,
    val createdAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TopicEmbeddingEntity

        if (topicId != other.topicId) return false
        if (categoryId != other.categoryId) return false
        if (title != other.title) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topicId.hashCode()
        result = 31 * result + categoryId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
