package com.debbly.server.category

import com.debbly.server.category.model.CategoryModel
import com.debbly.server.category.repository.CategoryCachedRepository
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val categoryCachedRepository: CategoryCachedRepository,
) {
    @GetMapping
    fun getAllCategories(): List<CategoryModel> {
        return categoryCachedRepository.findAll().filter { it.active }
    }
}
