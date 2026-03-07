package com.debbly.server.stage

import com.debbly.server.IdService
import com.debbly.server.claim.model.ClaimStance
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.claim.repository.ClaimJpaRepository
import com.debbly.server.claim.user.repository.UserClaimCachedRepository
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.livekit.LiveKitService
import com.debbly.server.livekit.egress.EgressService
import com.debbly.server.match.model.Match
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.pusher.model.PusherEventName.STAGE_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.STAGE_CLOSED
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.settings.LivekitConfig
import com.debbly.server.settings.SettingsService
import com.debbly.server.settings.repository.UserSettingsCachedRepository
import com.debbly.server.stage.event.AllHostsJoinedEvent
import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.model.StageType
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.stage.repository.StageMediaJpaRepository
import com.debbly.server.stage.repository.entities.CloseReason
import com.debbly.server.stage.repository.entities.CloseReason.*
import com.debbly.server.stage.repository.entities.StageMediaEntity
import com.debbly.server.stage.repository.entities.StageMediaStatus
import com.debbly.server.stage.repository.entities.StageVisibility
import com.debbly.server.stage.repository.entities.StageStatus
import com.debbly.server.stage.repository.entities.StageStatus.*
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
    private val egressService: EgressService,
    private val userSettingsRepository: UserSettingsCachedRepository,
    private val settingsService: SettingsService,
    private val clock: Clock,
    private val socialUsernameCachedRepository: com.debbly.server.user.repository.SocialUsernameCachedRepository,
    private val pusherService: PusherService,
    private val eventPublisher: ApplicationEventPublisher,
    private val matchRepository: MatchRepository,
    private val stageMediaRepository: StageMediaJpaRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Scheduler with virtual threads for delayed stage closure checks
    private val stageClosureScheduler =
        Executors.newScheduledThreadPool(1) { runnable ->
            Thread.ofVirtual().name("stage-closure-check").unstarted(runnable)
        }

    fun getUserHostedStages(userId: String): List<StageHistoryDetails> {
        val stages = stageRepository.findTop10ByHostUserId(userId)
        val claims =
            claimRepository
                .findByClaimIds(stages.mapNotNull { it.claimId })
                .associateBy { it.claimId }
        val mediaMap =
            stageMediaRepository
                .findByStageIdIn(stages.map { it.stageId })
                .associateBy { it.stageId }

        return stages.map { stage ->
            val claim = claims[stage.claimId]
            val media = mediaMap[stage.stageId]
            val hosts =
                stage.hosts.map { host ->
                    val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
                    val socials =
                        socialUsernameCachedRepository
                            .findAllByUserId(user.userId)
                            .associate { it.socialType to it.username }
                    Host(
                        userId = user.userId,
                        username = user.username ?: "unknown",
                        avatarUrl = user.avatarUrl,
                        stance = host.stance ?: ClaimStance.EITHER,
                        bio = user.bio,
                        socials = socials,
                    )
                }

            StageHistoryDetails(
                stageId = stage.stageId,
                claim =
                    claim?.let {
                        Claim(
                            claimId = it.claimId,
                            title = it.title,
                            categoryId = it.categoryId,
                        )
                    },
                hosts = hosts,
                status = stage.status,
                openedAt = stage.openedAt,
                closedAt = stage.closedAt,
                hlsUrl = getHlsUrlFromMedia(media, stage.status),
                thumbnailUrl = media?.thumbnailUrl,
            )
        }
    }

    fun getStageDetails(
        stageId: String,
        userId: String?,
    ): StageDetails {
        val stage = stageRepository.getById(stageId)
        val claim = stage.claimId?.let { claimCachedRepository.getById(it) }
        val media = stageMediaRepository.findById(stageId).orElse(null)
        val hosts =
            stage.hosts.map { host ->
                val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
                val stance = host.stance
                val socials =
                    socialUsernameCachedRepository
                        .findAllByUserId(user.userId)
                        .associate { it.socialType to it.username }

                Host(
                    userId = user.userId,
                    username = user.username ?: "unknown",
                    avatarUrl = user.avatarUrl,
                    stance = stance ?: ClaimStance.EITHER,
                    bio = user.bio,
                    socials = socials,
                )
            }

        val (isHost, livekitToken) =
            userId.let { tokenUserId ->
                val isHost = stage.hosts.any { it.userId == userId }
                val isOpenOrPending = stage.status in setOf(PENDING, OPEN)

                val userStance = stage.hosts.find { it.userId == userId }?.stance ?: ClaimStance.EITHER
                val role = if (isHost) "HOST" else "VIEWER"
                val metadata = """{"role":"$role","stance":"${userStance.name}"}"""

                val token =
                    if (isOpenOrPending) {
                        liveKitService.getToken(
                            userId = tokenUserId,
                            stageId = stageId,
                            canPublish = isHost,
                            metadata = metadata,
                        )
                    } else {
                        null
                    }

                isHost to token
            }

        return StageDetails(
            stageId = stage.stageId,
            eventId = stage.eventId,
            claim =
                claim?.let { it ->
                    Claim(
                        claimId = it.claimId,
                        title = claim.title,
                        categoryId = claim.categoryId,
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
            hlsUrl = getHlsUrlFromMedia(media, stage.status),
            thumbnailUrl = media?.thumbnailUrl,
            livekitConfig = settingsService.getLivekitClientConfig(),
        )
    }

    private fun getHlsUrlFromMedia(
        media: StageMediaEntity?,
        stageStatus: StageStatus,
    ): String? {
        if (media == null) return null
        return when (stageStatus) {
            OPEN -> media.hlsLiveLandscapeUrl
            CLOSED -> media.hlsLandscapeUrl
            else -> null
        }
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

            val hosts =
                match.opponents.map {
                    StageModel.StageHostModel(
                        userId = it.userId,
                        stance = it.stance,
                    )
                }

            val stage =
                StageModel(
                    stageId = stageId,
                    type = if (hosts.size == 1) StageType.SOLO else StageType.ONE_ON_ONE,
                    claimId = claim.claimId,
                    topicId = claim.topicId,
                    eventId = match.eventId,
                    challengeId = match.challengeId,
                    title = claim.title,
                    hosts = hosts,
                    createdAt = Instant.now(clock),
                    status = PENDING,
                    openedAt = null,
                    closedAt = null,
                )

            stageRepository.save(stage)
        }
    }

    fun heartbeat(
        stageId: String,
        userId: String,
    ) {
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

    fun onParticipantLeft(
        userId: String,
        stageId: String,
    ) {
        logger.info("participant_left: user=$userId stage=$stageId")

        val stage = stageRepository.getById(stageId)
        if (stage.status != OPEN) {
            logger.info("participant_left ignored: stage=$stageId status=${stage.status}")
            return
        }

        if (stage.hosts.none { it.userId == userId }) {
            logger.debug("participant_left: user=$userId is not a host of stage=$stageId, ignoring")
            return
        }

        logger.info("Host $userId left stage $stageId, scheduling closure check in 10 seconds")
        stageClosureScheduler.schedule(
            { checkIfHostStillAbsent(stageId, userId) },
            10,
            TimeUnit.SECONDS,
        )
    }

    private fun checkIfHostStillAbsent(
        stageId: String,
        userId: String,
    ) {
        try {
            val stage = stageRepository.findById(stageId)
            if (stage == null || stage.status != OPEN) {
                logger.info("checkIfHostStillAbsent: stage=$stageId already gone or closed (status=${stage?.status}), skipping")
                return
            }

            val participants = liveKitService.getParticipants(stageId)
            val participantIds = participants.map { it.identity }
            logger.info("checkIfHostStillAbsent: stage=$stageId participants=${participantIds}")

            if (userId !in participantIds) {
                logger.info("Host $userId still absent after grace period, closing stage $stageId")
                closeStage(stage, HOST_LEFT)
            } else {
                logger.info("Host $userId rejoined stage $stageId, not closing")
            }
        } catch (e: Exception) {
            logger.error("Error checking stage $stageId for host $userId closure", e)
        }
    }

    fun onUserJoined(
        userId: String,
        stageId: String,
    ) {
        logger.info("participant_joined: user=$userId stage=$stageId")

        val stage = stageRepository.getById(stageId)
        val allHostUserIds = stage.hosts.map { it.userId }
        val liveKitParticipants = liveKitService.getParticipants(stageId)
        val participantIds = liveKitParticipants.map { it.identity }

        logger.info("participant_joined: stage=$stageId status=${stage.status} currentParticipants=$participantIds expectedHosts=$allHostUserIds")

        removeDuplicateParticipants(stageId, liveKitParticipants)

        if (participantIds
                .toSet()
                .containsAll(allHostUserIds) &&
            stage.status == PENDING
        ) {
            logger.info("All hosts joined stage $stageId, publishing AllHostsJoinedEvent")
            eventPublisher.publishEvent(AllHostsJoinedEvent(stageId))
        }
    }

    private fun removeDuplicateParticipants(
        stageId: String,
        participants: List<LivekitModels.ParticipantInfo>,
    ) {
        val participantsByIdentity = participants.groupBy { it.identity }

        participantsByIdentity.forEach { (identity, participantList) ->
            if (participantList.size > 1) {
                logger.warn("Found ${participantList.size} duplicate participants for identity $identity in stage $stageId")

                val duplicatesToRemove = participantList.sortedBy { it.joinedAt }.dropLast(1)
                duplicatesToRemove.forEach { duplicate ->
                    try {
                        logger.info("Removing duplicate participant identity=${duplicate.identity} sid=${duplicate.sid} from stage=$stageId")
                        liveKitService.removeParticipant(stageId, duplicate.identity)
                    } catch (e: Exception) {
                        logger.error("Failed to remove duplicate participant ${duplicate.identity}", e)
                    }
                }
            }
        }
    }

    private fun stopEgressIfActive(stageId: String): EgressService.StopEgressResult? {
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

            val egress =
                egressService
                    .listAllEgresses(stageId)
                    .firstOrNull { it.egressId == egressId }

            if (egress == null) {
                return null
            }

            if (!egress.isActive) {
                return EgressService.StopEgressResult(
                    success = true,
                    startedAt = egress.startedAtMillis,
                    endedAt = egress.endedAtMillis ?: clock.millis(),
                )
            }

            logger.debug("Stopping egress recording for stage $stageId, egressId: $egressId")
            val result = egressService.stopCompositeEgress(egress.egressId)

            if (!result.success) {
                logger.warn("Failed to stop egress recording for stage $stageId, egressId: $egressId")
            }

            // Stop any remaining active egresses (e.g. thumbnail egress)
            stopRemainingEgresses(stageId, egressId)

            return result
        } catch (e: Exception) {
            logger.error("Error stopping egress for stage $stageId", e)
            return null
        }
    }

    private fun stopRemainingEgresses(
        stageId: String,
        excludeEgressId: String,
    ) {
        try {
            val remaining =
                egressService
                    .listActiveEgresses(stageId)
                    .filter { it.egressId != excludeEgressId }

            remaining.forEach { egress ->
                try {
                    egressService.stopCompositeEgress(egress.egressId)
                    logger.debug("Stopped remaining egress ${egress.egressId} for stage $stageId")
                } catch (e: Exception) {
                    logger.error("Failed to stop remaining egress ${egress.egressId} for stage $stageId", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error listing remaining egresses for stage $stageId", e)
        }
    }

    /**
     * Check for stages that have exceeded their time limit and close them
     * Uses Redis LiveStage cache for better performance
     */
    fun closeStagesByTimeout() {
        val now = Instant.now(clock)
        val timeLimitSeconds = settingsService.getStageDuration()
        val cutoffTime = now.minusSeconds(timeLimitSeconds)

        val expiredLiveStages =
            liveStageRedisRepository
                .findAll()
                .filter { liveStage -> liveStage.openedAt.isBefore(cutoffTime) }

        if (expiredLiveStages.isNotEmpty()) {
            logger.debug("Found ${expiredLiveStages.size} live stages to close due to timeout")
        }

        expiredLiveStages.forEach { liveStage ->
            try {
                val stage = stageRepository.findById(liveStage.stageId)
                if (stage != null) {
                    logger.info("Closing stage ${stage.stageId} due to time limit (${timeLimitSeconds / 60} minutes)")
                    closeStage(stage, TIMEOUT)
                } else {
                    logger.warn("Stage ${liveStage.stageId} found in Redis but not in database, cleaning up Redis")
                    liveStageRedisRepository.deleteById(liveStage.stageId)
                }
            } catch (e: Exception) {
                logger.error("Error closing expired stage ${liveStage.stageId}", e)
            }
        }

        val pendingCutoff = now.minusSeconds(120)
        val stalePendingStages = stageRepository.findStalePendingStages(pendingCutoff)

        if (stalePendingStages.isNotEmpty()) {
            logger.debug("Found ${stalePendingStages.size} pending stages to close due to 2-minute timeout")
        }

        stalePendingStages.forEach { stage ->
            try {
                logger.info("Closing pending stage ${stage.stageId} due to 2-minute timeout")
                closeStage(stage, TIMEOUT)
            } catch (e: Exception) {
                logger.error("Error closing stale pending stage ${stage.stageId}", e)
            }
        }
    }

    private fun closeStage(
        stage: StageModel,
        reason: CloseReason,
    ) {
        val currentStage = stageRepository.findById(stage.stageId)
        if (currentStage == null || currentStage.status == CLOSED) {
            logger.info("closeStage: stage=${stage.stageId} already closed or missing, cleaning up Redis")
            liveStageRedisRepository.deleteById(stage.stageId)
            return
        }
        logger.info("closeStage: closing stage=${stage.stageId} reason=$reason")

        val closedStage =
            currentStage.copy(
                status = CLOSED,
                closedAt = Instant.now(clock),
                closeReason = reason,
            )
        stageRepository.save(closedStage)

        val egressResult = stopEgressIfActive(stage.stageId)
        updateStageMedia(closedStage, egressResult)

        liveStageRedisRepository.deleteById(stage.stageId)

        liveKitService.endRoom(stage.stageId)
        notifyStageClosed(stage.stageId, reason)

        logger.info("Closed stage ${stage.stageId}, reason: $reason")
    }

    private fun updateStageMedia(
        stage: StageModel,
        egressResult: EgressService.StopEgressResult?,
    ) {
        val media = stageMediaRepository.findById(stage.stageId).orElse(null) ?: return

        if (media.status == StageMediaStatus.NOT_RECORDED) return

        val egressDuration =
            egressResult?.let {
                val startedAt = it.startedAt ?: return@let null
                val endedAt = it.endedAt ?: return@let null
                (endedAt - startedAt) / 1000
            }

        val durationSeconds = egressDuration
            ?: run {
                val openedAt = stage.openedAt
                val closedAt = stage.closedAt
                if (openedAt != null && closedAt != null) closedAt.epochSecond - openedAt.epochSecond else null
            }

        val newStatus = when {
            egressResult?.success != true || egressDuration == null -> StageMediaStatus.FAILED
            egressDuration > 180 -> StageMediaStatus.RECORDED
            else -> StageMediaStatus.NOT_RECORDED
        }

        stageMediaRepository.save(media.copy(status = newStatus, durationSeconds = durationSeconds))

        val isRecorded = newStatus == StageMediaStatus.RECORDED
        stageRepository.save(stage.copy(isRecorded = isRecorded))
    }

    fun setStageVisibility(
        stageId: String,
        userId: String,
        visibility: StageVisibility,
    ) {
        val stage =
            stageRepository.findById(stageId)
                ?: throw IllegalArgumentException("Stage not found")

        if (stage.hosts.none { it.userId == userId }) {
            throw UnauthorizedException("User is not a host of this stage")
        }

        val updatedHosts = stage.hosts.map { host ->
            if (host.userId == userId) host.copy(visibility = visibility) else host
        }

        val computedVisibility = if (updatedHosts.any { it.visibility == StageVisibility.HOST_ONLY })
            StageVisibility.HOST_ONLY else StageVisibility.PUBLIC

        stageRepository.save(stage.copy(hosts = updatedHosts, visibility = computedVisibility))
        logger.info("Stage $stageId visibility set to $computedVisibility by host $userId (host preference: $visibility)")
    }

    fun deleteStage(
        stageId: String,
        userId: String,
    ) {
        val stage =
            stageRepository.findById(stageId)
                ?: throw IllegalArgumentException("Stage not found")

        if (stage.hosts.none { it.userId == userId }) {
            throw UnauthorizedException("User is not a host of this stage")
        }

        if (stage.status == OPEN) {
            closeStage(stage, HOST_DELETED)
        }

        val currentStage = stageRepository.findById(stageId) ?: return
        stageRepository.save(currentStage.copy(visibility = StageVisibility.HOST_ONLY))

        logger.info("Stage $stageId deleted by host $userId")
    }

    private fun notifyStageClosed(
        stageId: String,
        reason: CloseReason,
    ) {
        try {
            val data =
                mapOf(
                    "data" to
                        mapOf(
                            "stageId" to stageId,
                            "reason" to reason.name.lowercase(),
                        ),
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
        val eventId: String?,
        val claim: Claim?,
        val isHost: Boolean,
        val hosts: List<Host>,
        val token: String?,
        val status: StageStatus,
        val createdAt: Instant,
        val openedAt: Instant?,
        val closedAt: Instant?,
        val limitMinutes: Long,
        val hlsUrl: String?,
        val thumbnailUrl: String?,
        val livekitConfig: LivekitConfig,
    )

    data class StageHistoryDetails(
        val stageId: String,
        val claim: Claim?,
        val hosts: List<Host>,
        val status: StageStatus,
        val openedAt: Instant?,
        val closedAt: Instant?,
        val hlsUrl: String?,
        val thumbnailUrl: String?,
    )

    data class Host(
        val userId: String,
        val username: String,
        val avatarUrl: String?,
        val stance: ClaimStance,
        val bio: String?,
        val socials: Map<SocialType, String>?,
    )

    data class Claim(
        val claimId: String,
        val title: String,
        val categoryId: String,
    )
}
