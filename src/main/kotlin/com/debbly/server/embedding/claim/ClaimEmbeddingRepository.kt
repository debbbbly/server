package com.debbly.server.embedding.claim

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class ClaimEmbeddingRepository(
    @Qualifier("pgvectorJdbcTemplate")
    private val jdbcTemplate: JdbcTemplate
) {
    fun save(entity: ClaimEmbeddingEntity) {
        val vectorLiteral = entity.embedding.toVectorLiteral()
        val sql = """
            INSERT INTO claim_embeddings (claim_id, title, category_id, embedding, created_at)
            VALUES (?, ?, ?, CAST(? AS vector), ?)
            ON CONFLICT (claim_id) DO UPDATE
            SET title = EXCLUDED.title,
                category_id = EXCLUDED.category_id,
                embedding = EXCLUDED.embedding
        """.trimIndent()

        jdbcTemplate.update(
            sql,
            entity.claimId,
            entity.title,
            entity.categoryId,
            vectorLiteral,
            entity.createdAt.toSqlTimestamp()
        )
    }

    /**
     * Find the most similar claims using pgvector's cosine distance operator (<=>)
     * Lower distance means more similar (0 = identical, 2 = opposite)
     * Cosine distance = 1 - cosine similarity
     */
    fun findSimilarByEmbedding(
        embedding: String,
        minSimilarity: Double,
        limit: Int
    ): List<SimilarClaimProjection> {
        val sql = """
            SELECT claim_id, title, category_id,
                   1 - (embedding <=> CAST(? AS vector)) as similarity
            FROM claim_embeddings
            WHERE 1 - (embedding <=> CAST(? AS vector)) >= ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            similarClaimRowMapper,
            embedding,
            embedding,
            minSimilarity,
            embedding,
            limit
        )
    }

    /**
     * Check if embedding exists for a claim
     */
    fun existsByClaimId(claimId: String): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM claim_embeddings WHERE claim_id = ?)"
        return jdbcTemplate.queryForObject(sql, Boolean::class.java, claimId) ?: false
    }

    /**
     * Update the categoryId for an existing claim embedding
     */
    fun updateCategoryId(claimId: String, categoryId: String) {
        val sql = "UPDATE claim_embeddings SET category_id = ? WHERE claim_id = ?"
        jdbcTemplate.update(sql, categoryId, claimId)
    }

    private val similarClaimRowMapper = RowMapper { rs, _ ->
        SimilarClaimProjection(
            claimId = rs.getString("claim_id"),
            title = rs.getString("title"),
            categoryId = rs.getString("category_id"),
            similarity = rs.getDouble("similarity")
        )
    }
}

data class SimilarClaimProjection(
    val claimId: String,
    val title: String,
    val categoryId: String,
    val similarity: Double
)

private fun FloatArray.toVectorLiteral(): String {
    return joinToString(prefix = "[", postfix = "]")
}

private fun Instant.toSqlTimestamp(): Timestamp {
    return Timestamp.from(this)
}
