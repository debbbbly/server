package com.debbly.server.user

import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service

@Service
class OnlineUsersService(
    //private val webSocketSessionService: WebSocketSessionService,
    private val userCachedRepository: UserCachedRepository
) {

    fun getOnlineUsers(): List<ListUserResponse> {
//        val connectedUserIds = webSocketSessionService.getOnlineUserIds()
//
//        return connectedUserIds.mapNotNull { userId ->
//            userCachedRepository.findById(userId)?.let { user ->
//                ListUserResponse(
//                    userId = user.userId,
//                    username = user.username,
//                    avatarUrl = user.avatarUrl
//                )
//            }
//        }

        return emptyList()
    }

    fun isUserOnline(userId: String): Boolean {
        // return webSocketSessionService.isUserOnline(userId)
        return true
    }

    fun getOnlineUserCount(): Int {
        // return webSocketSessionService.getOnlineUserCount()
        return 99
    }
}

data class ListUserResponse(
    val userId: String,
    val username: String?,
    val avatarUrl: String?,
    val rank: Int? = 0
)
