package com.debbly.server.claim.repository

import com.debbly.server.claim.model.ClaimSide
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant


@Entity(name = "users_claims_sides")
data class UserClaimSideEntity(
    @EmbeddedId
    val id: UserClaimSideId,
    @Enumerated(EnumType.STRING)
    val side: ClaimSide,
    val categoryId: String,
    val updatedAt: Instant,
)

@Embeddable
data class UserClaimSideId(
    val claimId: String,
    val userId: String
) : Serializable
