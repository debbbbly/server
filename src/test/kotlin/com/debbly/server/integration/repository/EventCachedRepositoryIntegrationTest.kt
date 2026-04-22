package com.debbly.server.integration.repository

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.event.model.EventStatus
import com.debbly.server.event.repository.EventCachedRepository
import com.debbly.server.event.repository.EventEntity
import com.debbly.server.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class EventCachedRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: EventCachedRepository

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
        insertUser("host")
        insertTopic("t1")
        insertClaim("c1", topicId = "t1")
    }

    @Test
    fun `save then findById round-trip`() {
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val event = EventEntity(
            eventId = "e1",
            claimId = "c1",
            hostUserId = "host",
            hostStance = ClaimStance.FOR,
            startTime = now,
            status = EventStatus.SCHEDULED,
            description = "desc",
            bannerImageUrl = null,
            createdAt = now,
            updatedAt = now,
        )

        repo.save(event)
        val found = repo.findById("e1")

        assertNotNull(found)
        assertEquals("e1", found?.eventId)
        assertEquals("host", found?.hostUserId)
    }

    @Test
    fun `findById returns null when event missing`() {
        assertNull(repo.findById("missing"))
    }
}
