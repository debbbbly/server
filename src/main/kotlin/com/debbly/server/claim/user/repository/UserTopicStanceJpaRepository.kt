package com.debbly.server.claim.user.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserTopicStanceJpaRepository : JpaRepository<UserTopicStanceEntity, UserTopicStanceId> {
    @Query("SELECT ut FROM users_topics ut JOIN FETCH ut.topic t WHERE ut.id.userId = :userId ORDER BY ut.updatedAt DESC")
    fun findByIdUserId(userId: String): List<UserTopicStanceEntity>

    fun findByIdUserIdAndIdTopicId(userId: String, topicId: String): UserTopicStanceEntity?

    @Query("SELECT ut FROM users_topics ut WHERE ut.id.userId = :userId AND ut.id.topicId IN :topicIds")
    fun findByUserIdAndTopicIds(userId: String, topicIds: List<String>): List<UserTopicStanceEntity>

    fun deleteByIdUserIdAndIdTopicId(userId: String, topicId: String)
}
