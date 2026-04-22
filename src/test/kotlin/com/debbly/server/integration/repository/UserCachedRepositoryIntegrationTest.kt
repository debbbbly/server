package com.debbly.server.integration.repository

import com.debbly.server.integration.AbstractIntegrationTest
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class UserCachedRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: UserCachedRepository

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
    }

    @Test
    fun `save then find by id, external id, username`() {
        val user = UserModel(
            userId = "u1",
            externalUserId = "ext-u1",
            email = "u1@test.dev",
            username = "Alice",
            usernameNormalized = "alice",
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        )
        repo.save(user)

        assertEquals("u1", repo.getById("u1").userId)
        assertNotNull(repo.findById("u1"))
        assertEquals("u1", repo.findByExternalUserId("ext-u1")?.userId)
        assertEquals("u1", repo.findByUsername("ALICE")?.userId)
    }

    @Test
    fun `findByIds returns empty map when input is empty`() {
        assertEquals(emptyMap<String, UserModel>(), repo.findByIds(emptyList()))
    }

    @Test
    fun `findByIds fetches from DB and caches`() {
        insertUser("u1")
        insertUser("u2")

        val result = repo.findByIds(listOf("u1", "u2"))

        assertEquals(setOf("u1", "u2"), result.keys)
    }

    @Test
    fun `findById returns null when user does not exist`() {
        assertNull(repo.findById("missing"))
    }
}
