package com.debbly.server.user

import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OnlineUsersService(
    private val userCachedRepository: UserCachedRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    companion object {
        private const val ONLINE_USERS_KEY = "online:users"

        // User is considered online if they heartbeated within this window.
        // Client should heartbeat every ~60s, so 5 minutes gives plenty of slack.
        private const val ONLINE_TTL_SECONDS = 300L
    }

    /**
     * Called on Pusher channel_occupied or client heartbeat.
     * Stores current timestamp as the score so staleness can be detected later.
     */
    fun markUserOnline(userId: String) {
        val now = Instant.now().epochSecond.toDouble()
        redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, userId, now)
    }

    /**
     * Called on Pusher channel_vacated for an immediate, reliable removal.
     */
    fun markUserOffline(userId: String) {
        redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, userId as Any)
    }

    fun isUserOnline(userId: String): Boolean {
        val score = redisTemplate.opsForZSet().score(ONLINE_USERS_KEY, userId as Any) ?: return false
        val cutoff = Instant.now().epochSecond - ONLINE_TTL_SECONDS
        return score > cutoff
    }

    fun areUsersOnline(userIds: List<String>): Map<String, Boolean> {
        if (userIds.isEmpty()) return emptyMap()
        return userIds.associateWith { isUserOnline(it) }
    }

    fun getOnlineUserIds(): Set<String> {
        val cutoff = (Instant.now().epochSecond - ONLINE_TTL_SECONDS).toDouble()
        return redisTemplate.opsForZSet()
            .rangeByScore(ONLINE_USERS_KEY, cutoff, Double.MAX_VALUE)
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()
    }

    fun getOnlineUsers(): List<ListUserResponse> {
        val onlineUserIds = getOnlineUserIds().toList()
        if (onlineUserIds.isEmpty()) return emptyList()

        return userCachedRepository.findByIds(onlineUserIds)
            .values
            .filter { !it.deleted }
            .map { user ->
                ListUserResponse(
                    userId = user.userId,
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    rank = user.rank
                )
            }
    }

    fun getOnlineUserCount(): Int {
        val cutoff = (Instant.now().epochSecond - ONLINE_TTL_SECONDS).toDouble()
        return redisTemplate.opsForZSet()
            .count(ONLINE_USERS_KEY, cutoff, Double.MAX_VALUE)
            ?.toInt() ?: 0
    }
}

data class ListUserResponse(
    val userId: String,
    val username: String?,
    val avatarUrl: String?,
    val rank: Int? = 0
)
