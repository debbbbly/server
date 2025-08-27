package com.debbly.server.claim.tag

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "tags")
class TagEntity(
    @Id
    val tagId: String,
    val title: String
)
