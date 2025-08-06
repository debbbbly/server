package com.debbly.server.stages.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

@RedisHash("liveStage")
data class LiveStageRedisDto(
    @Id val stageId: String,
    val type: StageType,
    val claimId: String?,
    var heartbeatAt: Instant
)