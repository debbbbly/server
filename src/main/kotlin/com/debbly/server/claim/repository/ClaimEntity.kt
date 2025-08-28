package com.debbly.server.claim.repository

import com.debbly.server.category.CategoryEntity
import com.debbly.server.claim.tag.TagEntity
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
    val tags: Set<TagEntity>,
    val popularity: Int?
)

