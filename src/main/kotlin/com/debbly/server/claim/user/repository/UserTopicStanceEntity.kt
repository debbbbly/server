package com.debbly.server.claim.user.repository

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.topic.repository.TopicEntity
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

@Entity(name = "users_topics")
data class UserTopicStanceEntity(
    @EmbeddedId
    val id: UserTopicStanceId,
    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("topicId")
    @JoinColumn(name = "topic_id")
    val topic: TopicEntity,
    @Enumerated(EnumType.STRING)
    val stance: ClaimStance,
    val updatedAt: Instant,
)

@Embeddable
data class UserTopicStanceId(
    val topicId: String,
    val userId: String
) : Serializable
