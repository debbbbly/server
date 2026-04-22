package com.debbly.server.integration.repository

import com.debbly.server.integration.AbstractIntegrationTest
import com.debbly.server.match.model.MatchRequest
import com.debbly.server.match.repository.MatchQueueRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class MatchQueueRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: MatchQueueRepository

    @BeforeEach
    fun setup() {
        flushRedis()
    }

    private fun request(userId: String) = MatchRequest(
        userId = userId,
        joinedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

    @Test
    fun `save then find round-trip`() {
        repo.save(request("u1"))

        val found = repo.find("u1")
        assertNotNull(found)
        assertEquals("u1", found?.userId)
        assertEquals(1L, repo.count())
    }

    @Test
    fun `remove deletes queue entry`() {
        repo.save(request("u1"))
        repo.remove("u1")
        assertNull(repo.find("u1"))
        assertEquals(0L, repo.count())
    }

    @Test
    fun `findAll returns everything saved`() {
        repo.save(request("u1"))
        repo.save(request("u2"))

        val all = repo.findAll()
        assertEquals(setOf("u1", "u2"), all.map { it.userId }.toSet())
    }

    @Test
    fun `removeAll wipes the queue`() {
        repo.save(request("u1"))
        repo.save(request("u2"))
        repo.removeAll()
        assertEquals(0L, repo.count())
    }
}
