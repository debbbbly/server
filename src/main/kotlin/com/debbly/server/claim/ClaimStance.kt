package com.debbly.server.claim

import com.debbly.server.user.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import java.io.Serializable
import java.time.Instant

enum class Stance {
    PRO,
    BOTH,
    CON
}

@Entity(name = "claim_stances")
data class ClaimStanceEntity(

    @EmbeddedId
    val id: ClaimStanceId,
    @Enumerated(EnumType.STRING)
    val stance: Stance,
    val updatedAt: Instant
)

@Embeddable
data class ClaimStanceId(
    val claimId: String,
    val userId: String
) : Serializable