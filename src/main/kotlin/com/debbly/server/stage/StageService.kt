package com.debbly.server.stage

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.livekit.LiveKitService
import com.debbly.server.livekit.S3LiveKitProperties
import com.debbly.server.match.model.Match
import com.debbly.server.pusher.model.PusherEventName
import com.debbly.server.pusher.model.PusherEventName.*
import com.debbly.server.pusher.model.PusherMessage
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.STAGE_CLOSED
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.settings.SettingsService
import com.debbly.server.settings.repository.UserSettingsCachedRepository
import com.debbly.server.stage.config.StageProperties
import com.debbly.server.stage.event.AllHostsJoinedEvent
import com.debbly.server.stage.model.LiveStageEntity
import com.debbly.server.stage.model.LiveStageHost
import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.StageType
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.user.SocialType
import com.debbly.server.user.repository.UserCachedRepository
import com.debbly.server.match.repository.MatchRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class StageService(
    private val stageRepository: StageCachedRepository,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimCachedRepository: ClaimCachedRepository,
    private val claimRepository: ClaimJpaRepository,
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val liveKitService: LiveKitService,
    private val stageProperties: StageProperties,
    private val userSettingsRepository: UserSettingsCachedRepository,
    private val settingsService: SettingsService,
    private val s3Config: S3LiveKitProperties,
    private val clock: Clock,
    private val socialUsernameCachedRepository: com.debbly.server.user.repository.SocialUsernameCachedRepository,
    private val pusherService: PusherService,
    private val eventPublisher: ApplicationEventPublisher,
    private val matchRepository: MatchRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getUserHostedStages(userId: String): List<StageHistoryDetails> {
        val stages = stageRepository.findTop10ByHostUserId(userId)
        val claims = claimRepository.findByClaimIdInWithAllData(stages.mapNotNull { it.claimId })
            .associateBy { it.claimId }

        return stages.map { stage ->
            val claim = claims[stage.claimId]
            val hosts = stage.hosts.map { host ->
                val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
                val socials = socialUsernameCachedRepository.findAllByUserId(user.userId)
                    .associate { it.socialType to it.username }
                Host(
                    userId = user.userId,
                    username = user.username ?: "unknown",
                    avatarUrl = user.avatarUrl,
                    stance = host.stance ?: ClaimStance.EITHER,
                    bio = user.bio,
                    socials = socials
                )
            }

            StageHistoryDetails(
                stageId = stage.stageId,
                claim = claim?.let {
                    Claim(
                        claimId = it.claimId,
                        title = it.title,
                        tags = it.tags.map { tag -> Claim.Tag(tagId = tag.tagId, title = tag.title) },
                        category = Category(
                            categoryId = it.category.categoryId,
                            title = it.category.title,
                            avatarUrl = it.category.avatarUrl
                        )
                    )
                },
                hosts = hosts,
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                hlsUrl = stage.hlsUrl
            )
        }
    }

    fun getRecordedStages(): List<StageHistoryDetails> {
        val stages = stageRepository.findTop30RecordedStages()
        val claims = claimRepository.findByClaimIdInWithAllData(stages.mapNotNull { it.claimId })
            .associateBy { it.claimId }

        return stages.map { stage ->
            val claim = claims[stage.claimId]
            val hosts = stage.hosts.map { host ->
                val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
                val socials = socialUsernameCachedRepository.findAllByUserId(user.userId)
                    .associate { it.socialType to it.username }
                Host(
                    userId = user.userId,
                    username = user.username ?: "unknown",
                    avatarUrl = user.avatarUrl,
                    stance = host.stance ?: ClaimStance.EITHER,
                    bio = user.bio,
                    socials = socials
                )
            }

            StageHistoryDetails(
                stageId = stage.stageId,
                claim = claim?.let {
                    Claim(
                        claimId = it.claimId,
                        title = it.title,
                        tags = it.tags.map { tag -> Claim.Tag(tagId = tag.tagId, title = tag.title) },
                        category = Category(
                            categoryId = it.category.categoryId,
                            title = it.category.title,
                            avatarUrl = it.category.avatarUrl
                        )
                    )
                },
                hosts = hosts,
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                hlsUrl = stage.hlsUrl
            )
        }
    }

    fun getStageDetails(stageId: String, userId: String?): StageDetails {
        val stage = stageRepository.getById(stageId)
        val claim = stage.claimId?.let { claimCachedRepository.getById(it) }
        val hosts = stage.hosts.map { host ->
            val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
            val stance = host.stance
            val socials = socialUsernameCachedRepository.findAllByUserId(user.userId)
                .associate { it.socialType to it.username }

            Host(
                userId = user.userId,
                username = user.username ?: "unknown",
                avatarUrl = user.avatarUrl,
                stance = stance ?: ClaimStance.EITHER,
                bio = user.bio,
                socials = socials
            )
        }

        val (isHost, livekitToken) = userId.let { tokenUserId ->
            val isHost = stage.hosts.any { it.userId == userId }

            isHost to liveKitService.getToken(userId = tokenUserId, stageId = stageId, isHost = isHost)
        }

        return StageDetails(
            stageId = stage.stageId,
            claim = claim?.let { it ->
                Claim(
                    claimId = it.claimId,
                    title = claim.title,
                    tags = claim.tags.map { Claim.Tag(tagId = it.tagId, title = it.title) },
                    category = Category(
                        categoryId = claim.category.categoryId,
                        title = claim.category.title,
                        avatarUrl = claim.category.avatarUrl ?: ""
                    )
                )
            },
            isHost = isHost,
            hosts = hosts,
            token = livekitToken,
            status = stage.status,
            createdAt = stage.createdAt,
            openedAt = stage.openedAt,
            closedAt = stage.closedAt,
            limitMinutes = settingsService.getDebateStageDuration() / 60,
            hlsUrl = stage.hlsUrl
        )
    }

    fun createStage(claimId: String?, hosts: List<StageModel.StageHostModel>): StageModel {
        val stageId = idService.getId()
        val claim = claimId?.let { claimCachedRepository.getById(it) }
        val stage = StageModel(
            stageId = stageId,
            type = if (hosts.size == 1) StageType.SOLO else StageType.ONE_ON_ONE,
            claimId = claim?.claimId,
            title = claim?.title,
            hosts = hosts,
            createdAt = Instant.now(clock),
            status = StageStatus.PENDING,
            openedAt = null,
            closedAt = null
        )

        stageRepository.save(stage)

        return stage
    }

    fun createStage(match: Match): StageModel {
        val stageId = match.matchId
        return stageRepository.findById(stageId) ?: let {
            // Note: LiveKit room creation is now handled asynchronously via MatchEventListener
            // to avoid blocking the match creation process

            val claim = claimCachedRepository.getById(match.claim.claimId)

            val hosts = match.opponents.map {
                StageModel.StageHostModel(
                    userId = it.userId,
                    stance = it.stance
                )
            }

            val stage = StageModel(
                stageId = stageId,
                type = if (hosts.size == 1) StageType.SOLO else StageType.ONE_ON_ONE,
                claimId = claim.claimId,
                title = claim.title,
                hosts = hosts,
                createdAt = Instant.now(clock),
                status = StageStatus.PENDING,
                openedAt = null,
                closedAt = null
            )

            stageRepository.save(stage)
        }
    }

    fun live(stageId: String, userId: String): StageModel {
        val stage = if (stageId == userId) {
            stageRepository.save(
                StageModel(
                    stageId = idService.getId(),
                    type = StageType.SOLO,
                    hosts = listOf(
                        StageModel.StageHostModel(
                            userId = userId,
                            stance = null
                        )
                    ),
                    title = null,
                    claimId = null,
                    createdAt = Instant.now(clock),
                    status = StageStatus.PENDING,
                    openedAt = null,
                    closedAt = null
                )
            )
        } else {
            stageRepository.getById(stageId)

                ?.also { stage ->
                    if (stage.hosts.none { it.userId == userId }) {
                        throw UnauthorizedException("User is not a host of this stage")
                    }
                } ?: throw UnauthorizedException("Stage not found")
        }

        val users = stage.hosts.mapNotNull { userCachedRepository.findById(it.userId) }.associateBy { it.userId }

        val claim = stage.claimId?.let { claimCachedRepository.getById(it) }
        liveStageRedisRepository.save(
            LiveStageEntity(
                stageId = stageId,
                type = stage.type,
                hosts = stage.hosts.mapNotNull { host ->
                    users[host.userId]?.let { user ->
                        LiveStageHost(
                            userId = user.userId,
                            username = user.username ?: "unknown",
                            userUrl = user.avatarUrl,
                            stance = host.stance
                        )
                    }
                },
                claimId = stage.claimId,
                title = claim?.title,
                openedAt = Instant.now(clock),
                heartbeatAt = Instant.now(clock)
            )
        )

        return stage
    }

    fun heartbeat(stageId: String, userId: String) {
        val liveStage = liveStageRedisRepository.findById(stageId).orElseThrow()
        if (liveStage.hosts.none { it.userId == userId }) {
            throw UnauthorizedException("User is not a host of this stage")
        }
        liveStage.heartbeatAt = Instant.now(clock)
        liveStageRedisRepository.save(liveStage)
    }

    fun leaveStage(stageId: String, userId: String) {
        stageRepository.getById(stageId)?.let { stage ->
            if (stage.hosts.none { it.userId == userId }) {
                throw UnauthorizedException("User is not a host of this stage")
            }
            val closedStage = stage.copy(
                status = StageStatus.CLOSED,
                closedAt = Instant.now(clock)
            )
            stageRepository.save(closedStage)
            liveStageRedisRepository.deleteById(stageId)

            // Broadcast debate ended event
            broadcastDebateEnded(stageId, closedStage, "host_left")
        }
    }

    fun onUserLeft(userId: String, stageId: String) {
        logger.info("👋 User: '$userId' left from stage: '$stageId'.")

        val stage = stageRepository.getById(stageId)
        val allHostUserIds = stage.hosts.map { it.userId }
        logger.info("📋 Stage hosts: $allHostUserIds")

        val liveKitParticipants = liveKitService.getParticipants(stageId)
        logger.info("🔗 LiveKit participants: ${liveKitParticipants.map { it.identity }}")

        // Check if any hosts are still connected
        val connectedHosts = liveKitParticipants
            .map { it.identity }
            .filter { it in allHostUserIds }

        logger.info("🏠 Connected hosts: $connectedHosts")

        if (connectedHosts.isEmpty() && stage.status != StageStatus.CLOSED) {
            logger.info("🚨 No hosts remaining! Stopping egress and closing stage $stageId")
            // Stop egress recording before closing stage
            stopEgressIfActive(stageId)

            val closedStage = stage.copy(
                status = StageStatus.CLOSED,
                closedAt = Instant.now(clock)
            )
            stageRepository.save(closedStage)
            liveStageRedisRepository.deleteById(stage.stageId)

            // Delete the related match (has the same ID as stage)
            matchRepository.delete(stageId)
            logger.info("Deleted match for stage $stageId")

            // End the LiveKit room
            liveKitService.endRoom(stage.stageId)

            // Broadcast debate ended event
            broadcastDebateEnded(stageId, closedStage, "all_hosts_left")

            logger.info("Successfully closed stage ${stage.stageId} - all hosts left")
        } else {
            logger.info("⏳ ${connectedHosts.size} hosts still connected, keeping stage open")
        }
    }

    fun onUserJoined(userId: String, stageId: String) {
        logger.info("User: '$userId' joined stage: '$stageId'.")

        val stage = stageRepository.getById(stageId)
        val allHostUserIds = stage.hosts.map { it.userId }
        val liveKitParticipants = liveKitService.getParticipants(stageId)

        if (liveKitParticipants.map { it.identity }.toSet()
                .containsAll(allHostUserIds) && stage.status == StageStatus.PENDING
        ) {
            eventPublisher.publishEvent(AllHostsJoinedEvent(stageId))
        }
    }

    private fun stopEgressIfActive(stageId: String) {
        logger.info("🔍 Checking for active egress recording for stage $stageId")
        try {
            val liveStageOptional = liveStageRedisRepository.findById(stageId)
            if (liveStageOptional.isPresent) {
                val liveStage = liveStageOptional.get()
                val egressId = liveStage.egressId

                logger.info("📺 Found live stage with egressId: $egressId")

                if (egressId != null) {
                    logger.info("🛑 Stopping egress recording for stage $stageId, egressId: $egressId")
                    val result = liveKitService.stopEgress(egressId)

                    if (result.success) {
                        logger.info("✅ Successfully stopped egress recording for stage $stageId")

                        // Check if egress lasted more than 5 minutes (300 seconds)
                        if (result.startedAt != null && result.endedAt != null) {
                            val durationSeconds = result.endedAt - result.startedAt
                            if (durationSeconds > settingsService.getDebateStageRecordedThreshold()) {
                                val stage = stageRepository.getById(stageId)
                                val updatedStage = stage.copy(recorded = true)
                                stageRepository.save(updatedStage)
                                logger.info("✅ Marked stage $stageId as recorded (duration: ${durationSeconds}s)")
                            } else {
                                logger.info("⏱️ Stage $stageId not marked as recorded (duration: ${durationSeconds}s < 300s)")
                            }
                        }
                    } else {
                        logger.warn("❌ Failed to stop egress recording for stage $stageId, egressId: $egressId")
                    }
                } else {
                    logger.info("💡 No active egress recording found for stage $stageId (egressId is null)")
                }
            } else {
                logger.info("🔍 No live stage found in Redis for stage $stageId")
            }
        } catch (e: Exception) {
            logger.error("💥 Error stopping egress for stage $stageId", e)
        }
    }

    /**
     * Check for stages that have exceeded their time limit and close them
     * Uses Redis LiveStage cache for better performance
     */
    fun closeExpiredStages() {
        val now = Instant.now(clock)
        val timeLimitSeconds = settingsService.getDebateStageDuration()
        val cutoffTime = now.minusSeconds(timeLimitSeconds.toLong())

        // Find live stages (Redis) that have exceeded the time limit
        val expiredLiveStages = liveStageRedisRepository.findAll()
            .filter { liveStage -> liveStage.openedAt.isBefore(cutoffTime) }

//        logger.info("Found ${expiredLiveStages.size} expired live stages to close")

        expiredLiveStages.forEach { liveStage ->
            try {
                stopEgressIfActive(liveStage.stageId)

                val stage = stageRepository.findById(liveStage.stageId)
                if (stage != null) {
                    logger.info("Closing stage ${stage.stageId} due to time limit (${timeLimitSeconds / 60} minutes)")
                    closeStage(stage)
                } else {
                    logger.warn("Stage ${liveStage.stageId} found in Redis but not in database, cleaning up Redis")
                    liveStageRedisRepository.deleteById(liveStage.stageId)
                }
            } catch (e: Exception) {
                logger.error("Error closing expired stage ${liveStage.stageId}", e)
            }
        }
    }

    private fun closeStage(stage: StageModel) {

        val roomClosed = liveKitService.endRoom(stage.stageId)
        if (!roomClosed) {
            logger.warn("Failed to end LiveKit room for stage ${stage.stageId}")
        }

        val closedStage = stage.copy(
            status = StageStatus.CLOSED,
            closedAt = Instant.now(clock)
        )
        stageRepository.save(closedStage)
        liveStageRedisRepository.deleteById(stage.stageId)

        // Broadcast debate ended event to stage channel
        broadcastDebateEnded(stage.stageId, closedStage, "timeout")

        logger.info("Successfully closed stage ${stage.stageId} due to timeout")
    }

    private fun broadcastDebateEnded(stageId: String, stage: StageModel, reason: String) {
        try {
            val data = mapOf(
                "data" to mapOf(
                    "stageId" to stageId,
//                    "closedAt" to stage.closedAt,
                    "reason" to reason
                )
            )
            val message = message(STAGE_CLOSED, data)
            pusherService.sendChannelMessage(stageId, STAGE_EVENT, message)
            logger.info("Broadcast debate ended for stage $stageId, reason: $reason")
        } catch (e: Exception) {
            logger.error("Failed to broadcast debate ended for stage $stageId", e)
        }
    }

    data class StageDetails(
        val stageId: String,
        val claim: Claim?,
        val isHost: Boolean,
        val hosts: List<Host>,
        val token: String?,
        val status: StageStatus,
        val createdAt: Instant,
        val openedAt: Instant?,
        val closedAt: Instant?,
        val limitMinutes: Long,
        val hlsUrl: String?
    )

    data class StageHistoryDetails(
        val stageId: String,
        val claim: Claim?,
        val hosts: List<Host>,
        val status: StageStatus,
        val openedAt: Instant?,
        val closedAt: Instant?,
        val hlsUrl: String?
    )

    data class Host(
        val userId: String,
        val username: String,
        val avatarUrl: String?,
        val stance: ClaimStance,
        val bio: String?,
        val socials: Map<SocialType, String>?
    )

    data class Claim(
        val claimId: String,
        val title: String,
        val tags: List<Tag>,
        val category: Category
    ) {
        data class Tag(
            val tagId: String,
            val title: String
        )
    }

    data class Category(
        val categoryId: String,
        val title: String,
        val avatarUrl: String
    )
}
