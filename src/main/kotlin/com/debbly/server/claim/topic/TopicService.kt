package com.debbly.server.claim.topic

import com.debbly.server.ai.OpenAiService
import com.debbly.server.embedding.topic.TopicEmbeddingEntity
import com.debbly.server.embedding.topic.TopicEmbeddingRepository
import com.debbly.server.claim.topic.model.TopicModel
import com.debbly.server.claim.topic.model.toEntity
import com.debbly.server.claim.topic.model.toModel
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.topic.repository.TopicSimilarityEntity
import com.debbly.server.claim.topic.repository.TopicSimilarityRepository
import com.debbly.server.embedding.topic.SimilarTopicProjection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.Clock
import java.time.Instant

@Service
class TopicService(
    private val topicRepository: TopicRepository,
    private val topicEmbeddingRepository: TopicEmbeddingRepository,
    private val topicSimilarityRepository: TopicSimilarityRepository,
    private val openAiService: OpenAiService,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TOPIC_MATCH_THRESHOLD = 0.82
        private const val SIMILARITY_STORE_THRESHOLD = 0.68
        private const val MAX_SLUG_LENGTH = 50
    }

    /**
     * Generate a URL-friendly slug from title.
     * Example: "Gun Control Laws" -> "gun-control-laws"
     */
    private fun slugify(title: String): String {
        return Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .take(MAX_SLUG_LENGTH)
            .trimEnd('-')
    }

    /**
     * Generate a unique topic ID from title, handling collisions.
     */
    private fun generateTopicId(title: String): String {
        val baseSlug = slugify(title)
        if (baseSlug.isEmpty()) {
            return "topic-${System.currentTimeMillis()}"
        }

        // Check if base slug is available
        if (!topicRepository.existsById(baseSlug)) {
            return baseSlug
        }

        // Find unique slug by appending number
        var counter = 2
        while (counter < 100) {
            val candidateSlug = "$baseSlug-$counter"
            if (!topicRepository.existsById(candidateSlug)) {
                return candidateSlug
            }
            counter++
        }

        // Fallback with timestamp
        return "$baseSlug-${System.currentTimeMillis()}"
    }

    /**
     * Find or create a topic based on the topic text and categoryId.
     * - If a topic with similarity >= 0.90 exists, return it (using existing topic's categoryId)
     * - Otherwise, create a new topic with the provided categoryId
     * - Store similarity relationships for all topics with similarity > 0.65
     */
    @Transactional
    fun findOrCreateTopic(title: String, categoryId: String): TopicModel {
        logger.info("Finding or creating topic: '$title' with suggested categoryId: $categoryId")

        val embedding = openAiService.generateEmbedding(title)
            ?: throw IllegalStateException("Failed to generate embedding for topic: $title")

        val vectorLiteral = embedding.map { it.toFloat() }
            .toFloatArray()
            .joinToString(prefix = "[", postfix = "]")

        val mostSimilar = topicEmbeddingRepository.findMostSimilarTopic(
            embedding = vectorLiteral,
            minSimilarity = TOPIC_MATCH_THRESHOLD
        )

        if (mostSimilar != null) {
            logger.info("Found existing topic ${mostSimilar.topicId} with similarity ${mostSimilar.similarity}")

            if (mostSimilar.categoryId != categoryId) {
                logger.warn(
                    "Category mismatch: existing topic ${mostSimilar.topicId} has category '${mostSimilar.categoryId}', " +
                    "but new claim suggested '$categoryId'. Using existing topic's category."
                )
            }

            val existingTopic = topicRepository.findById(mostSimilar.topicId)
                .orElseThrow { IllegalStateException("Topic ${mostSimilar.topicId} not found in topics table") }
            return existingTopic.toModel()
        }

        // Create new topic with provided categoryId
        val now = Instant.now(clock)
        val newTopic = TopicModel(
            topicId = generateTopicId(title),
            categoryId = categoryId,
            title = title,
            createdAt = now,
            updatedAt = now
        )

        topicRepository.save(newTopic.toEntity())
        logger.info("Created new topic ${newTopic.topicId} with categoryId: $categoryId")

        // Save topic embedding
        val topicEmbedding = TopicEmbeddingEntity(
            topicId = newTopic.topicId,
            categoryId = categoryId,
            title = title,
            embedding = embedding.map { it.toFloat() }.toFloatArray(),
            createdAt = now
        )
        topicEmbeddingRepository.save(topicEmbedding)
        logger.info("Saved embedding for topic ${newTopic.topicId}")

        // Find all similar topics above the similarity threshold
        val similarTopics = topicEmbeddingRepository.findAllSimilarTopics(
            embedding = vectorLiteral,
            minSimilarity = SIMILARITY_STORE_THRESHOLD
        )

        // Store bidirectional similarity relationships
        val similaritiesCreated = storeSimilarities(newTopic.topicId, similarTopics, now)
        logger.info("Stored $similaritiesCreated similarity relationships for topic ${newTopic.topicId}")

        return newTopic
    }

    /**
     * Store bidirectional similarity relationships between the new topic and similar topics
     */
    private fun storeSimilarities(
        newTopicId: String,
        similarTopics: List<SimilarTopicProjection>,
        timestamp: Instant
    ): Int {
        var count = 0

        similarTopics.forEach { similar ->
            // Skip self-similarity
            if (similar.topicId == newTopicId) {
                return@forEach
            }

            // Store A -> B
            if (!topicSimilarityRepository.existsByTopicId1AndTopicId2(newTopicId, similar.topicId)) {
                topicSimilarityRepository.save(
                    TopicSimilarityEntity(
                        topicId1 = newTopicId,
                        topicId2 = similar.topicId,
                        similarity = similar.similarity,
                        createdAt = timestamp
                    )
                )
                count++
            }

            // Store B -> A (bidirectional)
            if (!topicSimilarityRepository.existsByTopicId1AndTopicId2(similar.topicId, newTopicId)) {
                topicSimilarityRepository.save(
                    TopicSimilarityEntity(
                        topicId1 = similar.topicId,
                        topicId2 = newTopicId,
                        similarity = similar.similarity,
                        createdAt = timestamp
                    )
                )
                count++
            }
        }

        return count
    }

    fun findById(topicId: String): TopicModel? {
        return topicRepository.findById(topicId).map { it.toModel() }.orElse(null)
    }

    fun findSimilarTopics(topicId: String): List<TopicSimilarityEntity> {
        return topicSimilarityRepository.findSimilarTopics(topicId)
    }

    /**
     * Calculate similarity between two topics and store it in the database
     * @return The calculated similarity value, or null if topics don't exist
     */
    @Transactional
    fun calculateAndStoreSimilarity(topicId1: String, topicId2: String): TopicSimilarityResult? {
        if (topicId1 == topicId2) {
            return TopicSimilarityResult(topicId1, topicId2, 1.0, stored = false, reason = "Same topic IDs")
        }

        // Verify both topics exist
        val topic1 = topicRepository.findById(topicId1).orElse(null)
            ?: return null
        val topic2 = topicRepository.findById(topicId2).orElse(null)
            ?: return null

        // Calculate similarity using pgvector
        val similarity = topicEmbeddingRepository.calculateSimilarity(topicId1, topicId2)
            ?: return TopicSimilarityResult(topicId1, topicId2, null, stored = false, reason = "Embeddings not found")

        val now = Instant.now(clock)
        var stored = false
        var reason = "Similarity below threshold ($SIMILARITY_STORE_THRESHOLD)"

        if (similarity >= SIMILARITY_STORE_THRESHOLD) {
            // Store bidirectional relationships
            if (!topicSimilarityRepository.existsByTopicId1AndTopicId2(topicId1, topicId2)) {
                topicSimilarityRepository.save(
                    TopicSimilarityEntity(
                        topicId1 = topicId1,
                        topicId2 = topicId2,
                        similarity = similarity,
                        createdAt = now
                    )
                )
                stored = true
            }
            if (!topicSimilarityRepository.existsByTopicId1AndTopicId2(topicId2, topicId1)) {
                topicSimilarityRepository.save(
                    TopicSimilarityEntity(
                        topicId1 = topicId2,
                        topicId2 = topicId1,
                        similarity = similarity,
                        createdAt = now
                    )
                )
            }
            reason = if (stored) "Stored successfully" else "Already exists"
        }

        logger.info("Calculated similarity between topics $topicId1 and $topicId2: $similarity (stored: $stored)")
        return TopicSimilarityResult(topicId1, topicId2, similarity, stored, reason)
    }
}

data class TopicSimilarityResult(
    val topicId1: String,
    val topicId2: String,
    val similarity: Double?,
    val stored: Boolean,
    val reason: String
)
