package com.debbly.server.claims

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity(name = "claims")
data class Claim(
    @Id
    val id: String,
    val topic: String,
    val text: String
)

