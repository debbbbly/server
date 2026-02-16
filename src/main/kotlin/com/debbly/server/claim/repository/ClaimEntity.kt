package com.debbly.server.claim.repository

import com.debbly.server.claim.model.StanceToTopic
import jakarta.persistence.*
import java.time.Instant

@Entity(name = "claims")
data class ClaimEntity(
    @Id
    val claimId: String,
    val categoryId: String,
    val title: String,
    val slug: String?,
    val createdAt: Instant,
    val topicId: String,
    @Enumerated(EnumType.STRING)
    val stanceToTopic: StanceToTopic,
    val removed: Boolean = false,
)
