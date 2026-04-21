package com.debbly.server.claim.topic.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface TopicSimilarityRepository : JpaRepository<TopicSimilarityEntity, TopicSimilarityId> {

    @Query("""
        SELECT ts FROM topic_similarities ts
        WHERE ts.topicId1 = :topicId
        ORDER BY ts.similarity DESC
    """)
    fun findSimilarTopics(topicId: String): List<TopicSimilarityEntity>

    fun existsByTopicId1AndTopicId2(topicId1: String, topicId2: String): Boolean
}
