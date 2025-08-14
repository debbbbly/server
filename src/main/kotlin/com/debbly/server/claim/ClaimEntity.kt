package com.debbly.server.claim

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.ManyToMany

@Entity(name = "claims")
data class ClaimEntity(
    @Id
    val claimId: String,

    @ManyToMany(fetch = FetchType.EAGER)
    val categories: Set<CategoryEntity>,

    val title: String,

    @ManyToMany(fetch = FetchType.EAGER)
    val tags: Set<TagEntity>
)

