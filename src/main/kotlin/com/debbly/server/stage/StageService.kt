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
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.pusher.model.PusherEventName.STAGE_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.STAGE_CLOSED
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.settings.SettingsService
import com.debbly.server.settings.repository.UserSettingsCachedRepository
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
import livekit.LivekitModels
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    // Scheduler with virtual threads for delayed stage closure checks
    private val stageClosureScheduler = Executors.newScheduledThreadPool(1) { runnable ->
        Thread.ofVirtual().name("stage-closure-check").unstarted(runnable)
    }

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
                        categoryId = it.categoryId
                    )
                },
                hosts = hosts,
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                hlsUrl = getHlsUrlForStageStatus(stage.hlsUrl, stage.status)
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
                        categoryId = it.categoryId
                    )
                },
                hosts = hosts,
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                hlsUrl = getHlsUrlForStageStatus(stage.hlsUrl, stage.status)
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
            val userStance = stage.hosts.find { it.userId == userId }?.stance ?: ClaimStance.EITHER
            val role = if (isHost) "HOST" else "VIEWER"
            val metadata = """{"role":"$role","stance":"${userStance.name}"}"""

            isHost to liveKitService.getToken(
                userId = tokenUserId,
                stageId = stageId,
                canPublish = isHost,
                metadata = metadata
            )
        }

        return StageDetails(
            stageId = stage.stageId,
            claim = claim?.let { it ->
                Claim(
                    claimId = it.claimId,
                    title = claim.title,
                    categoryId = claim.categoryId
                )
            },
            isHost = isHost,
            hosts = hosts,
            token = livekitToken,
            status = stage.status,
            createdAt = stage.createdAt,
            openedAt = stage.openedAt,
            closedAt = stage.closedAt,
            limitMinutes = settingsService.getStageDuration() / 60,
            hlsUrl = getHlsUrlForStageStatus(stage.hlsUrl, stage.status)
        )
    }

    private fun getHlsUrlForStageStatus(baseUrl: String?, status: StageStatus): String? {
        if (baseUrl == null) return null

        val playlistName = if (status == StageStatus.CLOSED) {
            "playlist.m3u8"
        } else {
            "playlist-live.m3u8"
        }

        return "$baseUrl/$playlistName"
    }

//    fun createStage(claimId: String?, hosts: List<StageModel.StageHostModel>): StageModel {
//        val stageId = idService.getId()
//        val claim = claimId?.let { claimCachedRepository.getById(it) }
//        val stage = StageModel(
//            stageId = stageId,
//            type = if (hosts.size == 1) StageType.SOLO else StageType.ONE_ON_ONE,
//            claimId = claim?.claimId,
//            title = claim?.title,
//            hosts = hosts,
//            createdAt = Instant.now(clock),
//            status = StageStatus.PENDING,
//            openedAt = null,
//            closedAt = null
//        )
//
//        stageRepository.save(stage)
//
//        return stage
//    }

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
                topicId = claim.topicId,
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

    fun openStage(stageId: String, userId: String): StageModel {
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
                    topicId = null,
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

//    fun leaveStage(stageId: String, userId: String) {
//        stageRepository.getById(stageId)?.let { stage ->
//            if (stage.hosts.none { it.userId == userId }) {
//                throw UnauthorizedException("User is not a host of this stage")
//            }
//            val closedStage = stage.copy(
//                status = StageStatus.CLOSED,
//                closedAt = Instant.now(clock)
//            )
//            stageRepository.save(closedStage)
//            liveStageRedisRepository.deleteById(stageId)
//
//            // Broadcast debate ended event
//            broadcastDebateEnded(stageId, closedStage, "host_left")
//        }
//    }

    fun onUserLeft(userId: String, stageId: String) {
        logger.debug("User $userId left from stage $stageId")

        val stage = stageRepository.getById(stageId)
        val allHostUserIds = stage.hosts.map { it.userId }

        if (userId !in allHostUserIds)
            return

        val liveKitParticipants = liveKitService.getParticipants(stageId)

        // Check if any hosts are still connected
        val connectedHosts = liveKitParticipants
            .map { it.identity }
            .filter { it in allHostUserIds }

        if (connectedHosts.isEmpty() && stage.status != StageStatus.CLOSED) {
            closeStageAfterAllHostsLeft(stage)
        } else if (!connectedHosts.contains(userId)) {
            // User left but others remain - schedule a check in 5 seconds
            // This gives a grace period for temporary disconnections (network hiccups, page reloads)
            logger.debug("User $userId left but ${connectedHosts.size} host(s) remain. Scheduling check in 5 seconds")
            stageClosureScheduler.schedule(
                { checkIfHostStillAbsent(stageId, userId) },
                5,
                TimeUnit.SECONDS
            )
        }
    }

    private fun checkIfHostStillAbsent(stageId: String, leftHostUserId: String) {
        try {
            val stage = stageRepository.findById(stageId)
            if (stage == null) {
                logger.debug("Stage $stageId no longer exists, skipping check")
                return
            }

            if (stage.status == StageStatus.CLOSED) {
                logger.debug("Stage $stageId already closed, skipping check")
                return
            }

            val liveKitParticipants = liveKitService.getParticipants(stageId)
            val participantIds = liveKitParticipants.map { it.identity }
            logger.debug("LiveKit participants after grace period: $participantIds")

            // Check if the user who left is still absent
            if (!participantIds.contains(leftHostUserId)) {
                logger.info("User $leftHostUserId still absent after grace period, closing stage $stageId")
                closeStageAfterAllHostsLeft(stage)
            } else {
                logger.debug("User $leftHostUserId rejoined stage $stageId, not closing")
            }
        } catch (e: Exception) {
            logger.error("Error checking stage $stageId for closure", e)
        }
    }

    private fun closeStageAfterAllHostsLeft(stage: StageModel) {
        closeStage(stage, "all_hosts_left")
    }

    fun onUserJoined(userId: String, stageId: String) {
        logger.debug("User $userId joined stage $stageId")

        val stage = stageRepository.getById(stageId)
        val allHostUserIds = stage.hosts.map { it.userId }
        val liveKitParticipants = liveKitService.getParticipants(stageId)

        removeDuplicateParticipants(stageId, liveKitParticipants)

        if (liveKitParticipants.map { it.identity }.toSet()
                .containsAll(allHostUserIds) && stage.status == StageStatus.PENDING
        ) {
            eventPublisher.publishEvent(AllHostsJoinedEvent(stageId))
        }
    }

    private fun removeDuplicateParticipants(stageId: String, participants: List<LivekitModels.ParticipantInfo>) {
        val participantsByIdentity = participants.groupBy { it.identity }

        participantsByIdentity.forEach { (identity, participantList) ->
            if (participantList.size > 1) {
                logger.warn("Found ${participantList.size} duplicate participants for identity $identity in stage $stageId")

                val duplicatesToRemove = participantList.sortedBy { it.joinedAt }.dropLast(1)
                duplicatesToRemove.forEach { duplicate ->
                    try {
                        liveKitService.removeParticipant(stageId, duplicate.sid)
                    } catch (e: Exception) {
                        logger.error("Failed to remove duplicate participant ${duplicate.sid}", e)
                    }
                }
            }
        }
    }

    private fun stopEgressIfActive(stageId: String): LiveKitService.StopEgressResult? {
        logger.debug("Checking for active egress recording for stage $stageId")
        try {
            val liveStageOptional = liveStageRedisRepository.findById(stageId)
            if (!liveStageOptional.isPresent) {
                logger.debug("No live stage found in Redis for stage $stageId")
                return null
            }

            val liveStage = liveStageOptional.get()
            val egressId = liveStage.egressId

            if (egressId == null) {
                logger.debug("No active egress recording found for stage $stageId")
                return null
            }

            val egress = liveKitService.listAllEgresses(stageId)
                .firstOrNull { it.egressId == egressId }

            if (egress == null) {
                return null
            }

            if (!egress.isActive) {
                return LiveKitService.StopEgressResult(
                    success = true,
                    startedAt = egress.startedAtMillis,
                    endedAt = egress.endedAtMillis ?: clock.millis(),
                )
            }

            logger.debug("Stopping egress recording for stage $stageId, egressId: $egressId")
            val result = liveKitService.stopEgress(egress.egressId)

            if (!result.success) {
                logger.warn("Failed to stop egress recording for stage $stageId, egressId: $egressId")
            }

            return result
        } catch (e: Exception) {
            logger.error("Error stopping egress for stage $stageId", e)
            return null
        }
    }

    private fun isStageRecorded(egressResult: LiveKitService.StopEgressResult?): Boolean {
        if (egressResult == null)
            return false

        val startedAt = egressResult.startedAt ?: return false
        val endedAt = egressResult.endedAt ?: return false

        val durationSeconds = (endedAt - startedAt) / 1000
        return durationSeconds > settingsService.getStageRecordedThreshold()
    }

    /**
     * Check for stages that have exceeded their time limit and close them
     * Uses Redis LiveStage cache for better performance
     */
    fun closeStagesByTimeout() {
        val now = Instant.now(clock)
        val timeLimitSeconds = settingsService.getStageDuration()
        val cutoffTime = now.minusSeconds(timeLimitSeconds)

        val expiredLiveStages = liveStageRedisRepository.findAll()
            .filter { liveStage -> liveStage.openedAt.isBefore(cutoffTime) }

        if (expiredLiveStages.isNotEmpty()) {
            logger.debug("Found ${expiredLiveStages.size} live stages to close due to timeout")
        }

        expiredLiveStages.forEach { liveStage ->
            try {
                val stage = stageRepository.findById(liveStage.stageId)
                if (stage != null) {
                    logger.info("Closing stage ${stage.stageId} due to time limit (${timeLimitSeconds / 60} minutes)")
                    closeStage(stage, "timeout")
                } else {
                    logger.warn("Stage ${liveStage.stageId} found in Redis but not in database, cleaning up Redis")
                    liveStageRedisRepository.deleteById(liveStage.stageId)
                }
            } catch (e: Exception) {
                logger.error("Error closing expired stage ${liveStage.stageId}", e)
            }
        }
    }

    private fun closeStage(stage: StageModel, reason: String) {
        val egressResult = stopEgressIfActive(stage.stageId)
        val recorded = isStageRecorded(egressResult)

        val closedStage = stage.copy(
            status = StageStatus.CLOSED,
            closedAt = Instant.now(clock),
            recorded = recorded
        )
        stageRepository.save(closedStage)
        liveStageRedisRepository.deleteById(stage.stageId)

        liveKitService.endRoom(stage.stageId)
        notifyStageClosed(stage.stageId, reason)

        logger.info("Closed stage ${stage.stageId}, reason: $reason, recorded: $recorded")
    }

    private fun notifyStageClosed(stageId: String, reason: String) {
        try {
            val data = mapOf(
                "data" to mapOf(
                    "stageId" to stageId,
                    "reason" to reason
                )
            )
            val message = message(STAGE_CLOSED, data)
            pusherService.sendChannelMessage(stageId, STAGE_EVENT, message)
            logger.debug("Broadcast stage closed notification for stage $stageId")
        } catch (e: Exception) {
            logger.error("Failed to broadcast stage closed for stage $stageId", e)
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
        val categoryId: String
    )
}
