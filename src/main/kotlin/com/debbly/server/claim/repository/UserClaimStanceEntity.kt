package com.debbly.server.claim.repository

import com.debbly.server.claim.model.ClaimStance
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant


@Entity(name = "users_claims_stances")
data class UserClaimStanceEntity(
    @EmbeddedId
    val id: UserClaimStanceId,
    @Enumerated(EnumType.STRING)
    val stance: ClaimStance,
    val categoryId: String,
    val updatedAt: Instant,
)

@Embeddable
data class UserClaimStanceId(
    val claimId: String,
    val userId: String
) : Serializable
