package com.debbly.server.stage

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.livekit.LiveKitService
import com.debbly.server.match.model.Match
import com.debbly.server.settings.SettingsName
import com.debbly.server.settings.UserSettingsName
import com.debbly.server.settings.repository.SettingsJpaRepository
import com.debbly.server.settings.repository.UserSettingsCachedRepository
import com.debbly.server.stage.config.StageProperties
import com.debbly.server.stage.model.LiveStageEntity
import com.debbly.server.stage.model.LiveStageHost
import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.StageType
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.user.repository.UserCachedRepository
import org.slf4j.LoggerFactory
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
    private val userClaimCachedRepository: UserClaimCachedRepository,
    private val liveKitService: LiveKitService,
    private val stageProperties: StageProperties,
    private val userSettingsRepository: UserSettingsCachedRepository,
    private val settingsRepository: SettingsJpaRepository,
    private val clock: Clock
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun getStageDetails(stageId: String, userId: String?): StageDetails {
        val stage = stageRepository.getById(stageId)
        val claim = stage.claimId?.let { claimCachedRepository.getById(it) }
        val hosts = stage.hosts.map { host ->
            val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
            val stance = host.stance

            StageDetails.Host(
                userId = user.userId,
                username = user.username ?: "unknown",
                avatarUrl = user.avatarUrl,
                stance = stance ?: ClaimStance.EITHER
            )
        }

        val (isHost, livekitToken) = userId.let { tokenUserId ->
            val isHost = stage.hosts.any { it.userId == userId }

            isHost to liveKitService.getToken(userId = tokenUserId, stageId = stageId, isHost = isHost)
        }


        return StageDetails(
            stageId = stage.stageId,
            claim = claim?.let { it ->
                StageDetails.Claim(
                    claimId = it.claimId,
                    title = claim.title,
                    tags = claim.tags.map { StageDetails.Claim.Tag(tagId = it.tagId, title = it.title) },
                    category = StageDetails.Category(
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
            limitMinutes = stageProperties.limitMinutes,
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

            val room = liveKitService.createRoom(stageId)
            if (room == null) {
                logger.error("Failed to create LiveKit room for stage: $stageId")
            } else {
                logger.info("Successfully created LiveKit room for stage: $stageId")
            }

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
            stageRepository.save(
                stage.copy(
                    status = StageStatus.CLOSED,
                    closedAt = Instant.now(clock)
                )
            )
            liveStageRedisRepository.deleteById(stageId)
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
            closeStage(stage)
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
            // All hosts joined, open the stage
            logger.info("All hosts joined stage: '$stageId'. Opening stage.")
            val openedAt = Instant.now(clock)
            val updatedStage = stage.copy(
                status = StageStatus.OPEN,
                openedAt = openedAt
            )
            stageRepository.save(updatedStage)

            // Update ranks for all hosts
            updateUserRanks(allHostUserIds)

            // Create live stage in Redis and start egress
            createLiveStageWithEgress(updatedStage, openedAt)
        }
    }

    private fun updateUserRanks(userIds: List<String>) {
        userIds.forEach { userId ->
            try {
                val stagesHosted = stageRepository.findAllByHostUserIdInLast30Days(userId)
                val newRank = stagesHosted.size

                val user = userCachedRepository.findById(userId)
                if (user != null) {
                    val updatedUser = user.copy(rank = newRank)
                    userCachedRepository.save(updatedUser)
                    logger.debug("Updated rank for user $userId to $newRank")
                }
            } catch (e: Exception) {
                logger.error("Error updating rank for user $userId", e)
            }
        }
    }

    private fun createLiveStageWithEgress(stage: StageModel, openedAt: Instant) {
        val users = stage.hosts.mapNotNull { userCachedRepository.findById(it.userId) }.associateBy { it.userId }
        val claim = stage.claimId?.let { claimCachedRepository.getById(it) }

        val shouldStartEgress = shouldStartEgressForStage(stage)

        val egressId = if (shouldStartEgress) {
            val egressInfo = try {
                liveKitService.startRoomEgress(stage.stageId)
            } catch (e: Exception) {
                logger.error("Failed to start egress for stage ${stage.stageId}", e)
                null
            }

            egressInfo?.egressId?.also {
                logger.info("Started egress recording for stage ${stage.stageId}, egressId: $it")
            } ?: run {
                logger.warn("Failed to start egress recording for stage ${stage.stageId}")
                null
            }
        } else {
            null
        }

        liveStageRedisRepository.save(
            LiveStageEntity(
                stageId = stage.stageId,
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
                openedAt = openedAt,
                heartbeatAt = Instant.now(clock),
                egressId = egressId
            )
        )
    }

    private fun shouldStartEgressForStage(stage: StageModel): Boolean {
        val maxEgressCount = settingsRepository.findById(SettingsName.STAGE_EGRESS_MAX_COUNT)
            .map { it.value.toIntOrNull() ?: 2 }
            .orElse(2)

        val currentActiveEgressCount = liveKitService.countActiveRoomCompositeEgresses()

        if (currentActiveEgressCount >= maxEgressCount) {
            logger.warn("Max egress limit reached ($currentActiveEgressCount/$maxEgressCount). Cannot start egress for stage ${stage.stageId}")
            return false
        }

        val hasUserRequiringEgress = stage.hosts.any { host ->
            val setting = userSettingsRepository.findByUserIdAndName(
                host.userId,
                UserSettingsName.ALWAYS_EGRESS_STAGE_HLS
            )
            setting?.value == "true"
        }

        return hasUserRequiringEgress
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
                    val stopped = liveKitService.stopEgress(egressId)
                    if (stopped) {
                        logger.info("✅ Successfully stopped egress recording for stage $stageId")
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
        val timeLimitSeconds = stageProperties.limitMinutes * 60L
        val cutoffTime = now.minusSeconds(timeLimitSeconds)

        // Find live stages (Redis) that have exceeded the time limit
        val expiredLiveStages = liveStageRedisRepository.findAll()
            .filter { liveStage -> liveStage.openedAt.isBefore(cutoffTime) }

//        logger.info("Found ${expiredLiveStages.size} expired live stages to close")

        expiredLiveStages.forEach { liveStage ->
            try {
                // Stop egress recording before closing expired stage
                stopEgressIfActive(liveStage.stageId)

                // Get the full stage model from database for closeStage()
                val stage = stageRepository.findById(liveStage.stageId)
                if (stage != null) {
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
        logger.info("Closing stage ${stage.stageId} due to time limit (${stageProperties.limitMinutes} minutes)")

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

        // TODO: Send WebSocket notification to participants about timeout
        // notifyStageTimeoutToParticipants(stage)

        logger.info("Successfully closed stage ${stage.stageId} due to timeout")
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
        val limitMinutes: Int,
        val hlsUrl: String?
    ) {
        data class Host(
            val userId: String,
            val username: String,
            val avatarUrl: String?,
            val stance: ClaimStance
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
}
