package com.debbly.server.claim.user.repository

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimEntity
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

@Entity(name = "users_claims")
data class UserClaimEntity(
    @EmbeddedId
    val id: UserClaimId,
    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("claimId")
    @JoinColumn(name = "claim_id")
    val claim: ClaimEntity,
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
