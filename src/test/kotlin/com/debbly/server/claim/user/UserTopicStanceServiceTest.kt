package com.debbly.server.claim.user

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.topic.repository.TopicEntity
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.user.repository.UserTopicStanceEntity
import com.debbly.server.claim.user.repository.UserTopicStanceJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class UserTopicStanceServiceTest {

    private val repo: UserTopicStanceJpaRepository = mock()
    private val topicRepo: TopicRepository = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val service = UserTopicStanceService(repo, topicRepo, clock)

    private fun topic() = TopicEntity(
        topicId = "t1",
        categoryId = "cat1",
        title = "topic",
        slug = null,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    @Test
    fun `updateStance skips when topic not found`() {
        whenever(topicRepo.findById("missing")).thenReturn(Optional.empty())
        service.updateStance("u1", "missing", ClaimStance.FOR)
        verify(repo, never()).save(any())
    }

    @Test
    fun `updateStance saves entity when topic exists`() {
        whenever(topicRepo.findById("t1")).thenReturn(Optional.of(topic()))
        service.updateStance("u1", "t1", ClaimStance.FOR)
        verify(repo).save(any())
    }

    @Test
    fun `findByUserIdAndTopicIds returns empty on empty input`() {
        assertEquals(emptyMap<String, ClaimStance>(), service.findByUserIdAndTopicIds("u1", emptyList()))
    }

    @Test
    fun `findByUserIdAndTopicIds associates topicId to stance`() {
        val entity = UserTopicStanceEntity(
            id = com.debbly.server.claim.user.repository.UserTopicStanceId("t1", "u1"),
            topic = topic(),
            stance = ClaimStance.FOR,
            updatedAt = Instant.parse("2025-01-01T00:00:00Z")
        )
        whenever(repo.findByUserIdAndTopicIds("u1", listOf("t1"))).thenReturn(listOf(entity))

        val result = service.findByUserIdAndTopicIds("u1", listOf("t1"))
        assertEquals(mapOf("t1" to ClaimStance.FOR), result)
    }
}
