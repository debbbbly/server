package com.debbly.server.home

import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.top.TopClaimResponse
import com.debbly.server.claim.top.TopClaimsService
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.topic.top.TopTopicsService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.claim.user.UserTopicStanceService
import com.debbly.server.home.model.*
import com.debbly.server.match.QueueService
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.stage.repository.StageMediaJpaRepository
import com.debbly.server.stage.repository.entities.StageEntity
import com.debbly.server.stage.repository.entities.StageMediaEntity
import com.debbly.server.stage.repository.entities.StageMediaStatus
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Service
class HomeService(
    private val topTopicsService: TopTopicsService,
    private val topClaimsService: TopClaimsService,
    private val stageJpaRepository: StageJpaRepository,
    private val claimJpaRepository: ClaimJpaRepository,
    private val claimCachedRepository: ClaimCachedRepository,
    private val userCachedRepository: UserCachedRepository,
    private val topicRepository: TopicRepository,
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val userTopicStanceService: UserTopicStanceService,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val queueService: QueueService,
    private val stageMediaRepository: StageMediaJpaRepository
) {

    /**
     * Get most recent stages (OPEN or with public media) with cursor pagination, regardless of topic.
     */
    fun getStages(cursor: String?, limit: Int): HomeStagesResponse {
        val cursorOpenedAt = cursor?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val stages = if (cursorOpenedAt != null) {
            stageJpaRepository.findRecentStagesBeforeCursor(cursorOpenedAt)
        } else {
            stageJpaRepository.findRecentStages()
        }.take(limit + 1)

        val hasMore = stages.size > limit
        val paginatedStages = stages.take(limit)

        val claimsMap = fetchClaimsMap(paginatedStages)
        val usersMap = fetchUsersMap(paginatedStages)
        val mediaMap = fetchMediaMap(paginatedStages)

        val stageResponses = paginatedStages.mapNotNull { stage ->
            claimsMap[stage.claimId]?.let { claim ->
                buildStageResponse(stage, claim, usersMap, mediaMap[stage.stageId])
            }
        }

        val nextCursor = if (hasMore) {
            paginatedStages.lastOrNull()?.openedAt?.toString()
        } else {
            null
        }

        return HomeStagesResponse(stages = stageResponses, nextCursor = nextCursor)
    }

    /**
     * Get top claims with rank-based cursor pagination.
     */
    fun getClaims(userId: String?, cursor: String?, limit: Int): HomeClaimsResponse {
        val topClaims = topClaimsService.getTopClaimsFromCache()

        val cursorRank = cursor?.toIntOrNull() ?: 0
        val filtered = topClaims
            .filter { it.rank > cursorRank }
            .take(limit + 1)

        val hasMore = filtered.size > limit
        val paginated = filtered.take(limit)

        val claimIds = paginated.map { it.claimId }.toSet()
        val userClaimStances = userId?.let { fetchUserClaimStances(it, claimIds.toList()) } ?: emptyMap()
        val claimQueues = queueService.getQueueByClaimIds(claimIds)

        val claimResponses = paginated.map {
            it.toHomeTopClaimResponse(userClaimStances[it.claimId], claimQueues[it.claimId] ?: emptyList())
        }

        val nextCursor = if (hasMore) {
            paginated.lastOrNull()?.rank?.toString()
        } else {
            null
        }

        return HomeClaimsResponse(claims = claimResponses, nextCursor = nextCursor)
    }

    /**
     * Get homepage data with topics and their stages.
     */
    fun getTopics(userId: String?, topicCursor: String?, topicLimit: Int, stageLimit: Int): HomeTopicsResponse {
        val topTopics = topTopicsService.getTopTopicsFromCache()

        val filteredTopics = topTopics
            .filter { it.rank > (topicCursor?.toIntOrNull() ?: 0) }
            .take(topicLimit)

        if (filteredTopics.isEmpty()) {
            return HomeTopicsResponse(topics = emptyList(), nextCursor = null)
        }

        val responses = getTopicsWithStages(
            userId = userId,
            topics = filteredTopics.map { TopicInfo(it.topicId, it.topicSlug, it.categoryId, it.title) },
            stageLimit = stageLimit,
            includeTotalStages = false
        )

        val nextCursor = if (topTopics.any { it.rank > (filteredTopics.lastOrNull()?.rank ?: Int.MAX_VALUE) }) {
            filteredTopics.lastOrNull()?.rank?.toString()
        } else {
            null
        }

        return HomeTopicsResponse(
            topics = responses,
            nextCursor = nextCursor
        )
    }

    /**
     * Get single topic detail with paginated stages.
     * Accepts either topicId or topicSlug for lookup.
     */
    fun getTopic(userId: String?, topicIdOrSlug: String, stageLimit: Int): HomeTopicResponse {
        val topic = topicRepository.findById(topicIdOrSlug).getOrNull()
            ?: topicRepository.findBySlug(topicIdOrSlug)
            ?: throw NoSuchElementException("Topic not found: $topicIdOrSlug")

        val topicInfo = TopicInfo(topic.topicId, topic.slug ?: topic.topicId, topic.categoryId, topic.title)

        return getTopicsWithStages(
            userId = userId,
            topics = listOf(topicInfo),
            stageLimit = stageLimit,
            includeTotalStages = true
        ).first()
    }

    /**
     * Get paginated stages for a topic using cursor-based pagination.
     * Cursor is the openedAt timestamp of the last stage seen (ISO-8601 format).
     * Accepts either topicId or topicSlug for lookup.
     */
    fun getTopicStages(topicIdOrSlug: String, stageCursor: String?, stagesLimit: Int): TopicStagesResponse {
        val topic = topicRepository.findById(topicIdOrSlug).getOrNull()
            ?: topicRepository.findBySlug(topicIdOrSlug)
            ?: throw NoSuchElementException("Topic not found: $topicIdOrSlug")

        val cursorOpenedAt = stageCursor?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }

        // Query with cursor-based pagination in SQL
        val stages = stageJpaRepository.findStagesByTopicIdPaginated(
            topicId = topic.topicId,
            cursorOpenedAt = cursorOpenedAt
        ).take(stagesLimit + 1)

        val hasMore = stages.size > stagesLimit
        val paginatedStages = stages.take(stagesLimit)

        val claimsMap = fetchClaimsMap(paginatedStages)
        val usersMap = fetchUsersMap(paginatedStages)
        val mediaMap = fetchMediaMap(paginatedStages)

        val stageResponses = paginatedStages.mapNotNull { stage ->
            claimsMap[stage.claimId]?.let { claim ->
                buildStageResponse(stage, claim, usersMap, mediaMap[stage.stageId])
            }
        }

        val nextCursor = if (hasMore) {
            paginatedStages.lastOrNull()?.openedAt?.toString()
        } else {
            null
        }

        return TopicStagesResponse(
            topicId = topic.topicId,
            topicSlug = topic.slug ?: topic.topicId,
            stages = stageResponses,
            nextCursor = nextCursor
        )
    }

    private fun getTopicsWithStages(
        userId: String?,
        topics: List<TopicInfo>,
        stageLimit: Int,
        includeTotalStages: Boolean
    ): List<HomeTopicResponse> {
        if (topics.isEmpty()) {
            return emptyList()
        }

        val topicIds = topics.map { it.topicId }.toSet()

        val allStages = stageJpaRepository.findStagesByTopicIds(topicIds.toList())
        // Take stageLimit + 1 per topic to check hasMore
        val stagesByTopic = allStages.groupBy { it.topicId }
            .mapValues { (_, stages) -> stages.take(stageLimit + 1) }

        // Fetch top claims grouped by topic
        val topClaimsByTopic = topClaimsService.getTopClaimsFromCache()
            .filter { it.topicId in topicIds }
            .groupBy { it.topicId }

        // Only fetch data for stages we'll actually return (not the +1 extras)
        val claimsMap = fetchClaimsMap(allStages)
        val usersMap = fetchUsersMap(allStages)
        val mediaMap = fetchMediaMap(allStages)

        // Fetch user stances if authenticated
        val userTopicStances = userId?.let { userTopicStanceService.findByUserIdAndTopicIds(it, topicIds.toList()) } ?: emptyMap()
        val userClaimStances = userId?.let { fetchUserClaimStances(it, topClaimsByTopic.values.flatten().map { c -> c.claimId }) } ?: emptyMap()

        // Fetch queue data
        val topicQueues = queueService.getQueueByTopicIds(topicIds)
        val claimIds = topClaimsByTopic.values.flatten().map { it.claimId }.toSet()
        val claimQueues = queueService.getQueueByClaimIds(claimIds)

        return topics.map { topic ->
            val topicStages = stagesByTopic[topic.topicId] ?: emptyList()
            val hasMore = topicStages.size > stageLimit
            val limitedStages = topicStages.take(stageLimit)

            val stageResponses = limitedStages.mapNotNull { stage ->
                claimsMap[stage.claimId]?.let { claim ->
                    buildStageResponse(stage, claim, usersMap, mediaMap[stage.stageId])
                }
            }

            val stagesCursor = if (hasMore) {
                limitedStages.lastOrNull()?.openedAt?.toString()
            } else {
                null
            }

            val totalStages = if (includeTotalStages) {
                stageJpaRepository.countStagesByTopicId(topic.topicId)
            } else {
                null
            }

            val topClaims = topClaimsByTopic[topic.topicId]
                ?.take(5)
                ?.map { it.toHomeTopClaimResponse(userClaimStances[it.claimId], claimQueues[it.claimId] ?: emptyList()) }
                ?: emptyList()

            HomeTopicResponse(
                topicId = topic.topicId,
                topicSlug = topic.topicSlug,
                categoryId = topic.categoryId,
                title = topic.title,
                claims = topClaims,
                stages = stageResponses,
                stagesCursor = stagesCursor,
                totalStages = totalStages,
                userStance = userTopicStances[topic.topicId],
                queue = topicQueues[topic.topicId] ?: emptyList()
            )
        }
    }

    private fun TopClaimResponse.toHomeTopClaimResponse(
        userStance: ClaimStance? = null,
        queue: List<QueueUserResponse> = emptyList()
    ) = HomeClaimResponse(
        claimId = claimId,
        claimSlug = claimSlug,
        categoryId = categoryId,
        title = title,
        forCount = forCount,
        againstCount = againstCount,
        userStance = userStance,
        queue = queue
    )

    private fun fetchUserClaimStances(userId: String, claimIds: List<String>): Map<String, ClaimStance> {
        if (claimIds.isEmpty()) return emptyMap()
        val claimIdSet = claimIds.toSet()
        return userClaimCachedRepository.findByUserId(userId)
            .filter { it.claim.claimId in claimIdSet }
            .associate { it.claim.claimId to it.stance }
    }

    private fun fetchClaimsMap(stages: List<StageEntity>): Map<String, ClaimModel> {
        val claimIds = stages.mapNotNull { it.claimId }.distinct()
        if (claimIds.isEmpty()) return emptyMap()
        return claimCachedRepository.findByIds(claimIds)
    }

    private fun fetchUsersMap(stages: List<StageEntity>): Map<String, UserModel> {
        val userIds = stages.flatMap { it.hosts.map { host -> host.id.userId } }.distinct()
        return userCachedRepository.findByIds(userIds)
    }

    private fun fetchMediaMap(stages: List<StageEntity>): Map<String, StageMediaEntity> {
        val stageIds = stages.map { it.stageId }
        if (stageIds.isEmpty()) return emptyMap()
        return stageMediaRepository.findByStageIdIn(stageIds).associateBy { it.stageId }
    }

    private fun buildStageResponse(
        stage: StageEntity,
        claim: ClaimModel,
        usersMap: Map<String, UserModel>,
        media: StageMediaEntity? = null
    ): HomeStageResponse {
        val hosts = stage.hosts.map { host ->
            val user = usersMap[host.id.userId]
            HomeHostResponse(
                userId = host.id.userId,
                username = user?.username ?: "unknown",
                avatarUrl = user?.avatarUrl,
                stance = host.stance
            )
        }

        val liveHlsUrl = if (stage.status == StageStatus.OPEN && media?.status == StageMediaStatus.IN_PROGRESS)
            media.hlsLiveLandscapeUrl else null
        val recording = if (stage.status == StageStatus.CLOSED && stage.isRecorded == true && media != null)
            StageRecordingResponse(media.hlsLandscapeUrl, media.hlsPortraitUrl, media.durationSeconds)
            else null

        return HomeStageResponse(
            stageId = stage.stageId,
            claim = HomeStageClaimResponse(claim.claimId, claim.slug, claim.categoryId, claim.title),
            hosts = hosts,
            status = stage.status,
            openedAt = stage.openedAt,
            closedAt = stage.closedAt,
            thumbnailUrl = media?.thumbnailUrl,
            liveHlsUrl = liveHlsUrl,
            recording = recording
        )
    }

    private data class TopicInfo(
        val topicId: String,
        val topicSlug: String,
        val categoryId: String,
        val title: String
    )

    /**
     * Get all currently live stages from Redis cache.
     */
    fun getLiveStages(): HomeLiveResponse {
        val liveStages = liveStageRedisRepository.findAll().toList()

        val stages = liveStages.map { liveStage ->
            HomeStageResponse(
                stageId = liveStage.stageId,
                claim = HomeStageClaimResponse(
                    claimId = liveStage.claimId ?: "",
                    claimSlug = liveStage.claimSlug,
                    categoryId = null,
                    title = liveStage.title ?: ""
                ),
                hosts = liveStage.hosts.map { host ->
                    HomeHostResponse(
                        userId = host.userId,
                        username = host.username,
                        avatarUrl = host.avatarUrl,
                        stance = host.stance
                    )
                },
                status = StageStatus.OPEN,
                openedAt = liveStage.openedAt,
                closedAt = null,
                thumbnailUrl = liveStage.thumbnailUrl
            )
        }

        return HomeLiveResponse(stages = stages)
    }
}
