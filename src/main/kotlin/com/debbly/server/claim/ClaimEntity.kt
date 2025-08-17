package com.debbly.server.claim

import jakarta.persistence.*

@Entity(name = "claims")
data class ClaimEntity(
    @Id
    val claimId: String,
    // TODO refactor to categoryId
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    val category: CategoryEntity,
    val title: String,
    @ManyToMany(fetch = FetchType.EAGER)
    val tags: Set<TagEntity>
)

