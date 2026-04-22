package com.debbly.server.integration.controller

import com.debbly.server.challenge.ChallengeService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.integration.AbstractIntegrationTest
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChallengeControllerIntegrationTest : AbstractIntegrationTest() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var challengeService: ChallengeService

    @Autowired
    private lateinit var userCachedRepository: UserCachedRepository

    private val restTemplate: RestTemplate = RestTemplateBuilder().build()

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
        insertTopic("t1")
        insertClaim("c1", topicId = "t1")
    }

    @Test
    fun `GET challenge by id returns 200 with response body`() {
        val host = userCachedRepository.save(
            UserModel(
                userId = "host",
                externalUserId = "ext-host",
                email = "host@test.dev",
                username = "host",
                usernameNormalized = "host",
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            )
        )
        val challenge = challengeService.create(host, "c1", ClaimStance.FOR)

        val response = restTemplate.getForEntity(
            "http://localhost:$port/challenges/${challenge.challengeId}",
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(challenge.challengeId, response.body?.get("challengeId"))
        assertEquals("c1", response.body?.get("claimId"))
    }

    @Test
    fun `GET missing challenge returns 404`() {
        val response = runCatching {
            restTemplate.getForEntity(
                "http://localhost:$port/challenges/does-not-exist",
                Map::class.java
            )
        }

        val status = response.fold(
            onSuccess = { it.statusCode },
            onFailure = { (it as org.springframework.web.client.HttpStatusCodeException).statusCode }
        )
        assertEquals(HttpStatus.NOT_FOUND, status)
    }
}
