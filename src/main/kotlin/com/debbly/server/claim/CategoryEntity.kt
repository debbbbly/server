package com.debbly.server.claim

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "categories")
class CategoryEntity(
    @Id
    val categoryId: String,
    val title: String,
    val avatarUrl: String,
    val active: Boolean = true
)
