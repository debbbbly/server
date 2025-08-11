package com.debbly.server

import io.livekit.server.AccessToken
import io.livekit.server.RoomJoin
import io.livekit.server.RoomName
import org.springframework.stereotype.Service

@Service
class LiveKitService(
    private val liveKitConfig: LiveKitConfig
) {

    companion object {
        private const val DEFAULT_TOKEN_TTL: Long = 60 * 15;
    }

    fun getToken(userId: String, stageId: String): String {

        val token = AccessToken(liveKitConfig.apiKey, liveKitConfig.apiSecret).apply {
            this.name = "UserId: $userId"
            this.identity = userId
            this.ttl = DEFAULT_TOKEN_TTL
            this.metadata = null

            addGrants(RoomJoin(true), RoomName(stageId));
        }

        return token.toJwt()
    }
}