package com.debbly.server.home.model

import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.stage.repository.entities.StageStatus
import java.time.Instant

data class HomeTopicsResponse(
    val topics: List<HomeTopicResponse>,
    val nextCursor: String?  // null if no more topics
)

data class HomeTopicResponse(
    val topicId: String,
    val topicSlug: String,
    val categoryId: String,
    val title: String,
    val claims: List<HomeTopClaimResponse>,
    val stages: List<HomeStageResponse>,
    val stagesCursor: String?,
    val totalStages: Int? = null,
    val liveStages: Int? = null,
    val userStance: ClaimStance? = null
)

data class HomeTopClaimResponse(
    val claimId: String,
    val claimSlug: String?,
    val title: String,
    val forCount: Int,
    val againstCount: Int,
    val userStance: ClaimStance? = null
)

data class HomeStageResponse(
    val stageId: String,
    val claim: HomeClaimResponse,
    val hosts: List<HomeHostResponse>,
    val status: StageStatus,
    val openedAt: Instant?,
    val closedAt: Instant?,
    val thumbnailUrl: String? = null
)

data class HomeLiveResponse(
    val stages: List<HomeStageResponse>
)

data class HomeClaimResponse(
    val claimId: String,
    val claimSlug: String?,
    val title: String
)

data class HomeHostResponse(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val stance: ClaimStance?
)

data class TopicStagesResponse(
    val topicId: String,
    val topicSlug: String,
    val stages: List<HomeStageResponse>,
    val nextCursor: String?
)