package com.debbly.server.embedding.claim

import java.time.Instant

data class ClaimEmbeddingEntity(
    val claimId: String,
    val title: String,
    val categoryId: String,
    val embedding: FloatArray,
    val createdAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClaimEmbeddingEntity

        if (claimId != other.claimId) return false
        if (title != other.title) return false
        if (categoryId != other.categoryId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = claimId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + categoryId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
