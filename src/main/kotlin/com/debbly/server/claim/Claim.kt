package com.debbly.server.claim

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity(name = "claims")
data class Claim(
    @Id
    val claimId: String,
    val topic: String,
    val text: String
)

