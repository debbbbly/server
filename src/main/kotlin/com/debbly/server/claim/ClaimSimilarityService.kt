package com.debbly.server.claim

import com.debbly.server.ai.OpenAiService
import com.debbly.server.embedding.repository.ClaimEmbeddingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ClaimSimilarityService(
    private val embeddingRepository: ClaimEmbeddingRepository,
    private val openAIService: OpenAiService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val DEFAULT_SIMILARITY_THRESHOLD = 0.65 // Very similar claims
        private const val DUPLICATE_THRESHOLD = 0.95 // Almost identical claims
    }

    /**
     * Find similar claims to the given text
     * @param text The claim text to find similarities for
     * @param limit Maximum number of similar claims to return (default 5)
     * @param minSimilarity Minimum similarity score (0-1) to include in results
     * @return List of similar claims with similarity scores, sorted by score descending
     */
    fun findSimilarClaims(
        text: String,
        limit: Int = 5,
        minSimilarity: Double = DEFAULT_SIMILARITY_THRESHOLD
    ): List<SimilarClaim> {
        val inputEmbedding = openAIService.generateEmbedding(text)

        if (inputEmbedding == null) {
            logger.error("Failed to generate embedding for text: $text")
            return emptyList()
        }

        return findSimilarClaimsByEmbedding(inputEmbedding, limit, minSimilarity)
    }

    fun findSimilarClaimsByEmbedding(
        embedding: List<Double>,
        limit: Int = 5,
        minSimilarity: Double = DEFAULT_SIMILARITY_THRESHOLD
    ): List<SimilarClaim> {
        return try {

            val results = embeddingRepository.findSimilarByEmbedding(
                embedding = "[${embedding.joinToString(",")}]",
                minSimilarity = minSimilarity,
                limit = limit
            )

            results.map { projection ->
                SimilarClaim(
                    claimId = projection.claimId,
                    title = projection.title,
                    categoryId = projection.categoryId,
                    similarity = projection.similarity,
                    isDuplicate = projection.similarity >= DUPLICATE_THRESHOLD
                )
            }
        } catch (e: Exception) {
            logger.error("Error finding similar claims: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Check if a claim text is a duplicate of an existing claim
     * Returns the duplicate claim if found, null otherwise
     */
    fun findDuplicate(text: String): SimilarClaim? {
        val similar = findSimilarClaims(text, limit = 1, minSimilarity = DUPLICATE_THRESHOLD)
        return similar.firstOrNull()?.takeIf { it.isDuplicate }
    }
}

data class SimilarClaim(
    val claimId: String,
    val title: String,
    val categoryId: String,
    val similarity: Double,
    val isDuplicate: Boolean
)
