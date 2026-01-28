package com.debbly.server.home

import com.debbly.server.claim.model.ClaimModel
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.model.toModel
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.top.TopClaimResponse
import com.debbly.server.claim.top.TopClaimsService
import com.debbly.server.claim.topic.repository.TopicRepository
import com.debbly.server.claim.topic.top.TopTopicsService
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.claim.user.UserTopicStanceService
import com.debbly.server.home.model.*
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageJpaRepository
import com.debbly.server.stage.repository.entities.StageEntity
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
    private val userCachedRepository: UserCachedRepository,
    private val topicRepository: TopicRepository,
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val userTopicStanceService: UserTopicStanceService,
    private val liveStageRedisRepository: LiveStageRedisRepository
) {
    companion object {
        private val STAGE_VISIBLE_STATUSES = listOf(StageStatus.OPEN, StageStatus.RECORDED)
    }

    /**
     * Get homepage data with topics and their stages.
     */
    fun getTopics(userId: String?, topicCursor: String?, topicLimit: Int, stageLimit: Int): HomeTopicsResponse {
        val allTopics = topTopicsService.getTopTopicsFromCache()

        val filteredTopics = allTopics
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

        val nextCursor = if (allTopics.any { it.rank > (filteredTopics.lastOrNull()?.rank ?: Int.MAX_VALUE) }) {
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
            statuses = STAGE_VISIBLE_STATUSES,
            cursorOpenedAt = cursorOpenedAt
        ).take(stagesLimit + 1)

        val hasMore = stages.size > stagesLimit
        val paginatedStages = stages.take(stagesLimit)

        val claimsMap = fetchClaimsMap(paginatedStages)
        val usersMap = fetchUsersMap(paginatedStages)

        val stageResponses = paginatedStages.mapNotNull { stage ->
            claimsMap[stage.claimId]?.let { claim ->
                buildStageResponse(stage, claim, usersMap)
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

        val allStages = stageJpaRepository.findStagesByTopicIds(topicIds.toList(), STAGE_VISIBLE_STATUSES)
        // Take stageLimit + 1 per topic to check hasMore
        val stagesByTopic = allStages.groupBy { it.topicId }
            .mapValues { (_, stages) -> stages.take(stageLimit + 1) }

        // Fetch top claims grouped by topic
        val topClaimsByTopic = topClaimsService.getTopClaimsFromCache()
            .filter { it.topicId in topicIds }
            .groupBy { it.topicId }

        // Only fetch data for stages we'll actually return (not the +1 extras)
        val stagesToEnrich = stagesByTopic.values.flatten().take(stageLimit)
        val claimsMap = fetchClaimsMap(allStages)
        val usersMap = fetchUsersMap(allStages)

        // Fetch user stances if authenticated
        val userTopicStances = userId?.let { userTopicStanceService.findByUserIdAndTopicIds(it, topicIds.toList()) } ?: emptyMap()
        val userClaimStances = userId?.let { fetchUserClaimStances(it, topClaimsByTopic.values.flatten().map { c -> c.claimId }) } ?: emptyMap()

        return topics.map { topic ->
            val topicStages = stagesByTopic[topic.topicId] ?: emptyList()
            val hasMore = topicStages.size > stageLimit
            val limitedStages = topicStages.take(stageLimit)

            val stageResponses = limitedStages.mapNotNull { stage ->
                claimsMap[stage.claimId]?.let { claim ->
                    buildStageResponse(stage, claim, usersMap)
                }
            }

            val stagesCursor = if (hasMore) {
                limitedStages.lastOrNull()?.openedAt?.toString()
            } else {
                null
            }

            val totalStages = if (includeTotalStages) {
                stageJpaRepository.countStagesByTopicId(topic.topicId, STAGE_VISIBLE_STATUSES)
            } else {
                null
            }

            val topClaims = topClaimsByTopic[topic.topicId]
                ?.take(5)
                ?.map { it.toHomeTopClaimResponse(userClaimStances[it.claimId]) }
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
                userStance = userTopicStances[topic.topicId]
            )
        }
    }

    private fun TopClaimResponse.toHomeTopClaimResponse(userStance: ClaimStance? = null) = HomeTopClaimResponse(
        claimId = claimId,
        claimSlug = claimSlug,
        title = title,
        forCount = forCount,
        againstCount = againstCount,
        userStance = userStance
    )

    private fun fetchUserClaimStances(userId: String, claimIds: List<String>): Map<String, ClaimStance> {
        if (claimIds.isEmpty()) return emptyMap()
        val claimIdSet = claimIds.toSet()
        return userClaimCachedRepository.findByUserId(userId)
            .filter { it.claim.claimId in claimIdSet }
            .associate { it.claim.claimId to it.stance }
    }

    private fun fetchClaimsMap(stages: List<StageEntity>): Map<String, ClaimModel> {
        return stages.mapNotNull { it.claimId }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { claimIds ->
                claimJpaRepository.findByClaimIds(claimIds)
                    .associate { it.claimId to it.toModel() }
            } ?: emptyMap()
    }

    private fun fetchUsersMap(stages: List<StageEntity>): Map<String, UserModel> {
        val userIds = stages.flatMap { it.hosts.map { host -> host.id.userId } }.distinct()
        return userCachedRepository.findByIds(userIds)
    }

    private fun buildStageResponse(
        stage: StageEntity,
        claim: ClaimModel,
        usersMap: Map<String, UserModel>
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

        return HomeStageResponse(
            stageId = stage.stageId,
            claim = HomeClaimResponse(claim.claimId, claim.slug, claim.title),
            hosts = hosts,
            status = stage.status,
            openedAt = stage.openedAt,
            closedAt = stage.closedAt
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
                claim = HomeClaimResponse(
                    claimId = liveStage.claimId ?: "",
                    claimSlug = liveStage.claimSlug,
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
                closedAt = null
            )
        }

        return HomeLiveResponse(stages = stages)
    }
}
