package com.debbly.server.claim.user

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.user.repository.UserTopicStanceEntity
import com.debbly.server.claim.user.repository.UserTopicStanceId
import com.debbly.server.claim.user.repository.UserTopicStanceJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class UserTopicStanceService(
    private val userTopicStanceJpaRepository: UserTopicStanceJpaRepository,
    private val topicRepository: TopicRepository,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun updateStance(userId: String, topicId: String, stance: ClaimStance) {
        val topic = topicRepository.findById(topicId).getOrNull()
            ?: run {
                logger.warn("Topic not found: {}", topicId)
                return
            }

        val entity = UserTopicStanceEntity(
            id = UserTopicStanceId(topicId = topicId, userId = userId),
            topic = topic,
            stance = stance,
            updatedAt = Instant.now(clock)
        )
        userTopicStanceJpaRepository.save(entity)
        logger.debug("Updated stance for user {} on topic {} to {}", userId, topicId, stance)
    }

    fun findByUserId(userId: String): List<UserTopicStanceEntity> {
        return userTopicStanceJpaRepository.findByIdUserId(userId)
    }

    fun findByUserIdAndTopicId(userId: String, topicId: String): UserTopicStanceEntity? {
        return userTopicStanceJpaRepository.findByIdUserIdAndIdTopicId(userId, topicId)
    }

    fun findByUserIdAndTopicIds(userId: String, topicIds: List<String>): Map<String, ClaimStance> {
        if (topicIds.isEmpty()) return emptyMap()
        return userTopicStanceJpaRepository.findByUserIdAndTopicIds(userId, topicIds)
            .associate { it.id.topicId to it.stance }
    }
}
