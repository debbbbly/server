package com.debbly.server.user

import com.debbly.server.user.repository.UserCachedRepository
import com.debbly.server.websocket.WebSocketSessionService
import org.springframework.stereotype.Service

@Service
class OnlineUsersService(
    private val webSocketSessionService: WebSocketSessionService,
    private val userCachedRepository: UserCachedRepository
) {

    fun getOnlineUsers(): List<ListUserResponse> {
        val connectedUserIds = webSocketSessionService.getOnlineUserIds()

        return connectedUserIds.mapNotNull { userId ->
            userCachedRepository.findById(userId)?.let { user ->
                ListUserResponse(
                    userId = user.userId,
                    username = user.username,
                    avatarUrl = user.avatarUrl
                )
            }
        }
    }

    fun isUserOnline(userId: String): Boolean {
        return webSocketSessionService.isUserOnline(userId)
    }

    fun getOnlineUserCount(): Int {
        return webSocketSessionService.getOnlineUserCount()
    }
}

data class ListUserResponse(
    val userId: String,
    val username: String?,
    val avatarUrl: String?,
    val rank: Int? = 0
)
