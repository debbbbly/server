package com.debbly.server.claim.topic.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TopicSimilarityRepository : JpaRepository<TopicSimilarityEntity, TopicSimilarityId> {

    /**
     * Find all topics similar to the given topic
     */
    @Query("""
        SELECT ts FROM topic_similarities ts
        WHERE ts.topicId1 = :topicId
        ORDER BY ts.similarity DESC
    """)
    fun findSimilarTopics(topicId: String): List<TopicSimilarityEntity>

    /**
     * Check if similarity relationship already exists
     */
    fun existsByTopicId1AndTopicId2(topicId1: String, topicId2: String): Boolean
}
