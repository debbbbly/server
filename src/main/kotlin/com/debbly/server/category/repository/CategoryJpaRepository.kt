package com.debbly.server.category.repository

import com.debbly.server.category.CategoryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryJpaRepository : JpaRepository<CategoryEntity, String>
