package com.debbly.server.claim.repository

import jakarta.persistence.*
import java.time.Instant

@Entity(name = "claims")
data class ClaimEntity(
    @Id
    val claimId: String,
    @Column(name = "category_id")
    val categoryId: String,
    val title: String,
    val popularity: Int?,
    val createdAt: Instant,
    var scoreFreshness: Double? = null,
    var scoreStancesRecent: Double? = null,
    var scoreDebatesRecent: Double? = null,
    var scoreBaseline: Double? = null,
    var scoreTotal: Double? = null
)
