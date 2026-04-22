package com.debbly.server.integration.service

import com.debbly.server.challenge.ChallengeService
import com.debbly.server.challenge.repository.ChallengeStatus
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.integration.AbstractIntegrationTest
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ChallengeServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var challengeService: ChallengeService

    @Autowired
    private lateinit var userCachedRepository: UserCachedRepository

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
        insertTopic("t1")
        insertClaim("c1", topicId = "t1")
    }

    private fun saveUser(id: String): UserModel =
        userCachedRepository.save(
            UserModel(
                userId = id,
                externalUserId = "ext-$id",
                email = "$id@test.dev",
                username = id,
                usernameNormalized = id,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            )
        )

    @Test
    fun `create then cancel round-trip`() {
        val host = saveUser("host")

        val created = challengeService.create(host, "c1", ClaimStance.FOR)
        assertEquals(ChallengeStatus.PENDING, created.status)
        assertEquals("c1", created.claimId)

        val cancelled = challengeService.cancel(host, created.challengeId)
        assertEquals(ChallengeStatus.CANCELLED, cancelled.status)
    }
}
