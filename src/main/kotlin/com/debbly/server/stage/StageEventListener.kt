package com.debbly.server.stage

import com.debbly.server.challenge.ChallengeService
import com.debbly.server.claim.repository.ClaimCachedRepository
import com.debbly.server.config.EgressLayout
import com.debbly.server.livekit.LiveKitService
import com.debbly.server.livekit.S3LiveKitProperties
import com.debbly.server.livekit.egress.EgressService
import com.debbly.server.match.MatchService
import com.debbly.server.match.QueueService
import com.debbly.server.match.repository.MatchRepository
import com.debbly.server.pusher.model.PusherEventName.STAGE_EVENT
import com.debbly.server.pusher.model.PusherMessage.Companion.message
import com.debbly.server.pusher.model.PusherMessageType.STAGE_OPEN
import com.debbly.server.pusher.service.PusherService
import com.debbly.server.settings.SettingsService
import com.debbly.server.settings.repository.UserSettingsCachedRepository
import com.debbly.server.stage.event.AllHostsJoinedEvent
import com.debbly.server.stage.model.LiveStageEntity
import com.debbly.server.stage.model.LiveStageHost
import com.debbly.server.stage.model.StageModel
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.stage.repository.StageMediaJpaRepository
import com.debbly.server.stage.repository.entities.StageMediaEntity
import com.debbly.server.stage.repository.entities.StageMediaStatus.IN_PROGRESS
import com.debbly.server.stage.repository.entities.StageStatus.OPEN
import com.debbly.server.stage.repository.entities.StageStatus.PENDING
import com.debbly.server.user.repository.UserCachedRepository
import livekit.LivekitModels
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.Instant.now
import java.util.concurrent.ConcurrentHashMap

@Component
class StageEventListener(
    private val stageRepository: StageCachedRepository,
    private val userCachedRepository: UserCachedRepository,
    private val egressService: EgressService,
    private val liveKitService: LiveKitService,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val claimCachedRepository: ClaimCachedRepository,
    private val userSettingsRepository: UserSettingsCachedRepository,
    private val settingsService: SettingsService,
    private val s3Config: S3LiveKitProperties,
    private val clock: Clock,
    private val pusherService: PusherService,
    private val matchRepository: MatchRepository,
    private val stageMediaRepository: StageMediaJpaRepository,
    private val matchService: MatchService,
    private val queueService: QueueService,
    private val challengeService: ChallengeService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val stagesBeingOpened = ConcurrentHashMap.newKeySet<String>()

    @EventListener
    @Async("stageEventExecutor")
    fun handleAllHostsJoined(event: AllHostsJoinedEvent) {
        logger.debug("Handling AllHostsJoinedEvent for stage ${event.stageId}")

        if (!stagesBeingOpened.add(event.stageId)) {
            return
        }

        try {
            val stage = stageRepository.getById(event.stageId)
            if (stage.status != PENDING) {
                return
            }

            logger.info("Opening stage ${event.stageId}")

            // Delete the match now that stage is opening (stageId = matchId)
            matchRepository.delete(event.stageId)

            val openedAt = now(clock)
            val updatedStage = stage.copy(
                status = OPEN,
                openedAt = openedAt
            )
            stageRepository.save(updatedStage)

            val allHostUserIds = updatedStage.hosts.map { it.userId }

            // Remove hosts from matching queue — they must manually rejoin after the debate
            allHostUserIds.forEach { matchService.removeFromQueue(it) }
            queueService.broadcastQueueUpdate()

            //move to a seperate listener later
            updateUserRanks(allHostUserIds)

            updatedStage.challengeId?.let { challengeId ->
                try {
                    challengeService.markAccepted(challengeId)
                } catch (e: Exception) {
                    logger.error("Failed to mark challenge $challengeId as accepted for stage ${event.stageId}", e)
                }
            }

            createLiveStageWithEgress(updatedStage, openedAt)
            broadcastDebateStarted(event.stageId, updatedStage)
        } catch (e: Exception) {
            logger.error("Failed to handle AllHostsJoinedEvent for stage ${event.stageId}", e)
        } finally {
            stagesBeingOpened.remove(event.stageId)
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
        val users = stage.hosts
            .mapNotNull { userCachedRepository.findById(it.userId) }
            .associateBy { it.userId }
        val claim = stage.claimId?.let { claimCachedRepository.getById(it) }

        val shouldStartEgress = shouldStartEgressForStage(stage)

        val (landscapeEgressId, portraitEgressId) = if (shouldStartEgress) {
            val allHostUserIds = stage.hosts.map { it.userId }
            if (!waitForHostsPublishing(stage.stageId, allHostUserIds)) {
                logger.warn("Hosts did not publish tracks in time for stage ${stage.stageId}, skipping egress")
            }

            val landscapeInfo = try {
                egressService.startCompositeEgress(stage.stageId, EgressLayout.LANDSCAPE)
            } catch (e: Exception) {
                logger.error("Failed to start landscape egress for stage ${stage.stageId}", e)
                null
            }

            val portraitInfo = try {
                egressService.startCompositeEgress(stage.stageId, EgressLayout.PORTRAIT)
            } catch (e: Exception) {
                logger.error("Failed to start portrait egress for stage ${stage.stageId}", e)
                null
            }

            logger.debug("Started landscape egress ${landscapeInfo?.egressId} and portrait egress ${portraitInfo?.egressId} for stage ${stage.stageId}")

            landscapeInfo?.egressId to portraitInfo?.egressId
        } else
            null to null

        stageMediaRepository.save(
            StageMediaEntity(
                stageId = stage.stageId,
                mediaPath = s3Config.buildMediaPath(stage.stageId),
                status = IN_PROGRESS,
                compositeEgressId = landscapeEgressId,
                portraitCompositeEgressId = portraitEgressId,
                createdAt = now(clock)
            )
        )

        try {
            egressService.startThumbnailEgress(stage.stageId)
        } catch (e: Exception) {
            logger.error("Failed to start thumbnail egress for stage ${stage.stageId}", e)
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
                            avatarUrl = user.avatarUrl,
                            stance = host.stance
                        )
                    }
                },
                claimId = stage.claimId,
                claimSlug = claim?.slug,
                title = claim?.title,
                openedAt = openedAt,
                heartbeatAt = now(clock),
                egressId = landscapeEgressId,
                portraitEgressId = portraitEgressId
            )
        )
    }

    private fun waitForHostsPublishing(stageId: String, hostUserIds: List<String>): Boolean {
        val maxAttempts = 3
        val delayMs = 1000L

        repeat(maxAttempts) { attempt ->
            val participants = liveKitService.getParticipants(stageId)
            val publishingHostIds = participants
                .filter { it.identity in hostUserIds }
                .filter { p -> p.tracksList.any { it.type == LivekitModels.TrackType.VIDEO } }
                .map { it.identity }

            if (publishingHostIds.containsAll(hostUserIds)) {
                logger.debug("All hosts publishing in stage $stageId after ${attempt + 1} attempt(s)")
                return true
            }

            logger.debug("Waiting for hosts to publish in stage $stageId (attempt ${attempt + 1}/$maxAttempts): publishing=$publishingHostIds, expected=$hostUserIds")
            Thread.sleep(delayMs)
        }

        logger.warn("Hosts did not start publishing video in stage $stageId after $maxAttempts attempts, starting egress anyway")
        return false
    }

    private fun shouldStartEgressForStage(stage: StageModel): Boolean {
        val maxEgressCount = settingsService.getHlsParallelLimit()
        val currentActiveEgressCount = egressService.countActiveRoomCompositeEgresses()

        if (currentActiveEgressCount >= maxEgressCount) {
            logger.warn("Max egress limit reached ($currentActiveEgressCount/$maxEgressCount), cannot start egress for stage ${stage.stageId}")
            return false
        }

        return true
    }

    private fun broadcastDebateStarted(stageId: String, stage: StageModel) {
        try {
            val data = mapOf(
                "stageId" to stageId
            )
            val message = message(STAGE_OPEN, data)
            pusherService.sendChannelMessage(stageId, STAGE_EVENT, message)
            logger.debug("Broadcast debate started notification for stage $stageId")
        } catch (e: Exception) {
            logger.error("Failed to broadcast debate started for stage $stageId", e)
        }
    }
}