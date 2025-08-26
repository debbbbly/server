package com.debbly.server.claim

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TagRepository : JpaRepository<TagEntity, String> {
    fun findByTitle(title: String): Optional<TagEntity>
}