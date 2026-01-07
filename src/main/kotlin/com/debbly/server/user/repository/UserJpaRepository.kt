package com.debbly.server.user.repository

import com.debbly.server.user.UserEntity
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.Optional

interface UserJpaRepository : JpaRepository<UserEntity, String> {
    fun findByUsername(username: String): Optional<UserEntity>
    fun findByUsernameNormalized(usernameNormalized: String): Optional<UserEntity>
    fun findByExternalUserId(externalUserId: String): Optional<UserEntity>
    fun findAllByUserIdIn(ids: List<String>): List<UserEntity>

    @Query("SELECT u FROM users u ORDER BY u.rank DESC")
    fun findTop100ByRankDesc(pageable: PageRequest): List<UserEntity>

    // Analytics: Count users active since timestamp
    @Query("SELECT COUNT(u) FROM users u WHERE u.lastSeen >= :since")
    fun countActiveUsersSince(since: Instant): Long

    // Analytics: Count users who logged in since timestamp
    @Query("SELECT COUNT(u) FROM users u WHERE u.lastLogin >= :since")
    fun countUserLoginsSince(since: Instant): Long

    // Analytics: Find users created between dates
    @Query("SELECT COUNT(u) FROM users u WHERE u.createdAt >= :start AND u.createdAt < :end")
    fun countUsersCreatedBetween(start: Instant, end: Instant): Long

    // Analytics: Find inactive users (no last_seen or old last_seen)
    @Query("SELECT u FROM users u WHERE u.lastSeen IS NULL OR u.lastSeen < :threshold ORDER BY u.createdAt DESC")
    fun findInactiveUsers(threshold: Instant): List<UserEntity>

    // Analytics: User retention - users created in period who are still active
    @Query("""
        SELECT COUNT(u) FROM users u
        WHERE u.createdAt >= :cohortStart
        AND u.createdAt < :cohortEnd
        AND u.lastSeen >= :activeThreshold
    """)
    fun countRetainedUsers(
        cohortStart: Instant,
        cohortEnd: Instant,
        activeThreshold: Instant
    ): Long

    @Query("SELECT u.userId FROM users u WHERE u.lastSeen >= :since")
    fun findUserIdsByLastSeenAfter(since: Instant): List<String>
}
