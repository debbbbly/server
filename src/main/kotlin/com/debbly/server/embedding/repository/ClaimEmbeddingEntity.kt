package com.debbly.server.embedding.repository

import java.time.Instant

data class ClaimEmbeddingEntity(
    val claimId: String,
    val title: String,
    val categoryId: String,
    val embedding: FloatArray,
    val createdAt: Instant,
    var updatedAt: Instant
)
