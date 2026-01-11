package com.debbly.server.embedding.topic

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class TopicEmbeddingRepository(
    @Qualifier("pgvectorJdbcTemplate")
    private val jdbcTemplate: JdbcTemplate
) {
    fun save(entity: TopicEmbeddingEntity) {
        val vectorLiteral = entity.embedding.toVectorLiteral()
        val sql = """
            INSERT INTO topic_embeddings (topic_id, category_id, title, embedding, created_at)
            VALUES (?, ?, ?, CAST(? AS vector), ?)
            ON CONFLICT (topic_id) DO UPDATE
            SET category_id = EXCLUDED.category_id,
                title = EXCLUDED.title,
                embedding = EXCLUDED.embedding
        """.trimIndent()

        jdbcTemplate.update(
            sql,
            entity.topicId,
            entity.categoryId,
            entity.title,
            vectorLiteral,
            entity.createdAt.toSqlTimestamp()
        )
    }

    /**
     * Find the most similar topic using pgvector's cosine distance operator (<=>)
     * Returns the most similar topic if it meets the minSimilarity threshold
     */
    fun findMostSimilarTopic(
        embedding: String,
        minSimilarity: Double
    ): SimilarTopicProjection? {
        val sql = """
            SELECT topic_id, category_id, title,
                   1 - (embedding <=> CAST(? AS vector)) as similarity
            FROM topic_embeddings
            WHERE 1 - (embedding <=> CAST(? AS vector)) >= ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT 1
        """.trimIndent()

        val results = jdbcTemplate.query(
            sql,
            similarTopicRowMapper,
            embedding,
            embedding,
            minSimilarity,
            embedding
        )

        return results.firstOrNull()
    }

    /**
     * Find all topics similar to the given embedding above the threshold
     * Used to store topic similarity relationships
     */
    fun findAllSimilarTopics(
        embedding: String,
        minSimilarity: Double
    ): List<SimilarTopicProjection> {
        val sql = """
            SELECT topic_id, category_id, title,
                   1 - (embedding <=> CAST(? AS vector)) as similarity
            FROM topic_embeddings
            WHERE 1 - (embedding <=> CAST(? AS vector)) >= ?
            ORDER BY embedding <=> CAST(? AS vector)
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            similarTopicRowMapper,
            embedding,
            embedding,
            minSimilarity,
            embedding
        )
    }

    /**
     * Check if embedding exists for a topic
     */
    fun existsByTopicId(topicId: String): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM topic_embeddings WHERE topic_id = ?)"
        return jdbcTemplate.queryForObject(sql, Boolean::class.java, topicId) ?: false
    }

    private val similarTopicRowMapper = RowMapper { rs, _ ->
        SimilarTopicProjection(
            topicId = rs.getString("topic_id"),
            categoryId = rs.getString("category_id"),
            title = rs.getString("title"),
            similarity = rs.getDouble("similarity")
        )
    }
}

data class SimilarTopicProjection(
    val topicId: String,
    val categoryId: String,
    val title: String,
    val similarity: Double
)

private fun FloatArray.toVectorLiteral(): String {
    return joinToString(prefix = "[", postfix = "]")
}

private fun Instant.toSqlTimestamp(): Timestamp {
    return Timestamp.from(this)
}
