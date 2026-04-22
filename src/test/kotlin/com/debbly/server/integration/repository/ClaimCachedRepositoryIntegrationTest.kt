package com.debbly.server.integration.repository

import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.StanceToTopic
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ClaimCachedRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: ClaimCachedRepository

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
        insertTopic("t1")
    }

    @Test
    fun `save then findById, findBySlug, getById round-trip`() {
        val claim = ClaimModel(
            claimId = "c1",
            categoryId = "society",
            title = "Should cats rule?",
            slug = "should-cats-rule",
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            topicId = "t1",
            stanceToTopic = StanceToTopic.FOR,
        )

        repo.save(claim)

        assertEquals("c1", repo.getById("c1").claimId)
        assertNotNull(repo.findById("c1"))
        assertEquals("c1", repo.findBySlug("should-cats-rule")?.claimId)
    }

    @Test
    fun `getById throws when missing`() {
        assertThrows<NoSuchElementException> { repo.getById("missing") }
    }

    @Test
    fun `findById returns null when missing`() {
        assertNull(repo.findById("missing"))
    }

    @Test
    fun `findByIds returns empty map when input is empty`() {
        assertEquals(emptyMap<String, ClaimModel>(), repo.findByIds(emptyList()))
    }

    @Test
    fun `findByIds fetches from DB`() {
        insertClaim("c1", topicId = "t1")
        insertClaim("c2", topicId = "t1")

        val result = repo.findByIds(listOf("c1", "c2"))

        assertEquals(setOf("c1", "c2"), result.keys)
    }
}
