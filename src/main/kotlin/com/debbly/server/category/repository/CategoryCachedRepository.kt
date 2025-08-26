package com.debbly.server.category.repository

import com.debbly.server.category.model.CategoryModel
import com.debbly.server.category.model.toEntity
import com.debbly.server.category.model.toModel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class CategoryCachedRepository(
    private val categoryJpaRepository: CategoryJpaRepository,
) {

    @Cacheable(value = ["categoriesByCategoryId"], key = "#categoryId")
    fun getById(categoryId: String): CategoryModel =
        categoryJpaRepository.findById(categoryId).getOrNull()?.toModel()
            ?: throw NoSuchElementException("Category not found")

    @Cacheable(value = ["categoriesByCategoryId"], key = "#categoryId", unless = "#result == null")
    fun findById(categoryId: String): CategoryModel? =
        categoryJpaRepository.findById(categoryId).getOrNull()?.toModel()

    @Cacheable(value = ["allCategories"])
    fun findAll(): List<CategoryModel> =
        categoryJpaRepository.findAll().map { it.toModel() }

    @CacheEvict(value = ["categoriesByCategoryId"], key = "#category.categoryId")
    fun save(category: CategoryModel) = categoryJpaRepository.save(category.toEntity()).toModel()

    @CacheEvict(value = ["categoriesByCategoryId"], key = "#categoryId")
    fun evictById(categoryId: String) {
        // This method only evicts the cache entry
    }

    @CacheEvict(value = ["categoriesByCategoryId", "allCategories"], allEntries = true)
    fun evictAll() {
        // This method evicts all cache entries for categories
    }

}