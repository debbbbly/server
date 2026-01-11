package com.debbly.server.claim.topic

import com.debbly.server.IdService
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
import java.time.Clock
import java.time.Instant

@Service
class TopicService(
    private val topicRepository: TopicRepository,
    private val topicEmbeddingRepository: TopicEmbeddingRepository,
    private val topicSimilarityRepository: TopicSimilarityRepository,
    private val openAiService: OpenAiService,
    private val idService: IdService,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TOPIC_MATCH_THRESHOLD = 0.90
        private const val SIMILARITY_STORE_THRESHOLD = 0.65
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

        // Generate embedding for the topic text
        val embedding = openAiService.generateEmbedding(title)
            ?: throw IllegalStateException("Failed to generate embedding for topic: $title")

        val embeddingVector = embedding.map { it.toFloat() }.toFloatArray()
        val vectorLiteral = embeddingVector.joinToString(prefix = "[", postfix = "]")

        // Find the most similar existing topic (search across all categories)
        val mostSimilar = topicEmbeddingRepository.findMostSimilarTopic(
            embedding = vectorLiteral,
            minSimilarity = TOPIC_MATCH_THRESHOLD
        )

        // If we found a very similar topic (>= 0.90), use it
        if (mostSimilar != null) {
            logger.info("Found existing topic ${mostSimilar.topicId} with similarity ${mostSimilar.similarity}")

            // Warn if categories differ
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
            topicId = idService.getId(),
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
            embedding = embeddingVector,
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
}
