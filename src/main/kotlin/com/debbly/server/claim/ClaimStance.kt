package com.debbly.server.claim

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable
import java.time.Instant

enum class ClaimStance {
    PRO,
    ANY,
    CON
}

@Entity(name = "users_claims_stances")
data class UserClaimStanceEntity(

    @EmbeddedId
    val id: UserClaimStanceId,
    @Enumerated(EnumType.STRING)
    val stance: ClaimStance,
    val updatedAt: Instant
)

@Embeddable
data class UserClaimStanceId(
    val claimId: String,
    val userId: String
) : Serializable