package com.debbly.server.integration

import com.redis.testcontainers.RedisContainer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var cacheManager: CacheManager

    @Autowired
    protected lateinit var redisConnectionFactory: RedisConnectionFactory

    protected fun clearAllCaches() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
    }

    protected fun flushRedis() {
        redisConnectionFactory.connection.use { it.serverCommands().flushAll() }
    }

    protected fun wipeDatabase() {
        listOf(
            "events",
            "challenges",
            "followers",
            "users_claims",
            "users_topics",
            "claims",
            "topic_similarities",
            "topics",
            "users",
        ).forEach { jdbcTemplate.execute("DELETE FROM $it") }
    }

    protected fun insertCategory(id: String = "society") {
        jdbcTemplate.update(
            "INSERT INTO categories (category_id, title) VALUES (?, ?) ON CONFLICT DO NOTHING",
            id,
            id
        )
    }

    protected fun insertUser(
        userId: String,
        username: String = userId,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO users (user_id, external_user_id, email, username, username_normalized, created_at, rank, deleted, banned)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 0, false, false)
            """.trimIndent(),
            userId,
            "ext-$userId",
            "$userId@test.dev",
            username,
            username.lowercase()
        )
    }

    protected fun insertTopic(topicId: String, categoryId: String = "society") {
        jdbcTemplate.update(
            """
            INSERT INTO topics (topic_id, category_id, title, created_at, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            topicId,
            categoryId,
            "topic-$topicId"
        )
    }

    protected fun insertClaim(
        claimId: String,
        topicId: String = "other",
        categoryId: String = "society",
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO claims (claim_id, category_id, title, slug, created_at, topic_id, stance_to_topic, removed)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, 'FOR', false)
            """.trimIndent(),
            claimId,
            categoryId,
            "title-$claimId",
            "slug-$claimId",
            topicId
        )
    }

    companion object {
        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("debbly")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() }

        private val redis: RedisContainer =
            RedisContainer(DockerImageName.parse("redis:7-alpine"))
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }

            registry.add("spring.datasource.pgvector.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.pgvector.username") { postgres.username }
            registry.add("spring.datasource.pgvector.password") { postgres.password }
            registry.add("spring.datasource.pgvector.driver-class-name") { "org.postgresql.Driver" }

            registry.add("spring.data.redis.url") { redis.redisURI }
        }
    }
}
