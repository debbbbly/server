package com.debbly.server.stage.model

import com.debbly.server.claim.model.ClaimStance
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import java.time.Instant

@RedisHash("liveStage")
data class LiveStageEntity(
    @Id val stageId: String,
    val type: StageType,
    var hosts: Collection<LiveStageHost>,
    val claimId: String?,
    val claimSlug: String?,
    val title: String?,
    val openedAt: Instant,
    var heartbeatAt: Instant,
    val egressId: String? = null,
    val portraitEgressId: String? = null,
    val thumbnailUrl: String? = null
)

data class LiveStageHost(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val stance: ClaimStance?
)