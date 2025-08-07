package com.debbly.server.stage.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

@RedisHash("liveStage")
data class LiveStageEntity(
    @Id val stageId: String,
    val type: StageType,
    var hosts: Collection<LiveStageHost>,
    val claimId: String?,
    var heartbeatAt: Instant
)

data class LiveStageHost(
    val userId: String,
    val username: String,
)