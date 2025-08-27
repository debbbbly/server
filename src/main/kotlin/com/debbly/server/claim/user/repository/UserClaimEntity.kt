package com.debbly.server.claim.user.repository

import com.debbly.server.claim.user.ClaimStance
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

@Entity(name = "users_claims")
data class UserClaimEntity(
    @EmbeddedId
    val id: UserClaimId,
    @Enumerated(EnumType.STRING)
    val stance: ClaimStance,
    val categoryId: String,
    val priority: Int?,
    val updatedAt: Instant,
)

@Embeddable
data class UserClaimId(
    val claimId: String,
    val userId: String
) : Serializable
