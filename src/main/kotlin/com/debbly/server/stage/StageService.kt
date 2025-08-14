package com.debbly.server.stage

import com.debbly.server.IdService
import com.debbly.server.LiveKitService
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.ClaimStance
import com.debbly.server.claim.ClaimStanceRepository
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.stage.model.*
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StageService(
//    private val stageRepository: stageRepository,
//    private val stageHostJpaRepository: StageHostJpaRepository,
    // private val liveStageRepository: LiveStageRepository,
    private val stageRepository: StageRepository,

    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimRepository,
    private val claimStanceRepository: ClaimStanceRepository,
    private val liveKitService: LiveKitService
) {

    fun getStageDetails(stageId: String, userId: String?): StageDetails {
        val stage = stageRepository.getById(stageId)
        val claim = stage.claimId?.let { claimRepository.findById(it).orElseThrow() }
        val hosts = stage.hosts.map { host ->
            val user = userCachedRepository.findById(host.userId) ?: throw Exception("User not found")
            val stance = host.stance

            StageDetails.Host(
                userId = user.userId,
                username = user.username ?: "unknown",
                avatarUrl = user.avatarUrl,
                stance = stance ?: ClaimStance.ANY
            )
        }

        val isHost = userId?.let { stage.hosts.any { it.userId == userId } } ?: false
        val livekitToken = userId?.takeIf { isHost }?.let {
            liveKitService.getToken(userId = userId, stageId = stageId)
        }

        return StageDetails(
            stageId = stage.stageId,
            claim = claim?.let { it ->
                StageDetails.Claim(
                    claimId = it.claimId,
                    title = claim.title,
                    tags = claim.tags.map { StageDetails.Claim.Tag(tagId = it.tagId, title = it.title) },
                    categories = claim.categories.map {
                        StageDetails.Category(
                            categoryId = it.categoryId,
                            title = it.title,
                            avatarUrl = it.avatarUrl ?: ""
                        )
                    }
                )
            },
            hosts = hosts,
            livekitToken = livekitToken
        )
    }

    fun createStage(claimId: String?, hosts: List<StageModel.StageHostModel>): StageModel {
        val stageId = idService.getId()
        val claim = claimId?.let { claimRepository.findById(it).orElseThrow() }
        val stage = StageModel(
            stageId = stageId,
            type = if (hosts.size == 1) StageType.SOLO else StageType.ONE_ON_ONE,
            claimId = claim?.claimId,
            title = claim?.title,
            hosts = hosts,
            createdAt = Instant.now(),
            closedAt = null,
        )

        stageRepository.save(stage)

        return stage
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
                    createdAt = Instant.now()
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

        liveStageRedisRepository.save(
            LiveStageEntity(
                stageId = stageId,
                type = stage.type,
                hosts = stage.hosts.mapNotNull { it ->
                    users[it.userId]?.let { user ->
                        LiveStageHost(
                            user.userId,
                            user.username ?: "unknown"
                        )
                    }
                },
                claimId = stage.claimId,
                heartbeatAt = Instant.now()
            )
        )

        return stage
    }

    fun heartbeat(stageId: String, userId: String) {
        val liveStage = liveStageRedisRepository.findById(stageId).orElseThrow()
        if (liveStage.hosts.none { it.userId == userId }) {
            throw UnauthorizedException("User is not a host of this stage")
        }
        liveStage.heartbeatAt = Instant.now()
        liveStageRedisRepository.save(liveStage)
    }

    fun leaveStage(stageId: String, userId: String) {
        stageRepository.getById(stageId)?.let { stage ->
            if (stage.hosts.none { it.userId == userId }) {
                throw UnauthorizedException("User is not a host of this stage")
            }
            stageRepository.save(stage.copy(closedAt = Instant.now()))
            liveStageRedisRepository.deleteById(stageId)
        }
    }

    data class StageDetails(
        val stageId: String,
        val claim: Claim?,
        val hosts: List<Host>,
        val livekitToken: String?
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
            val categories: List<Category>
        ) {
            data class Tag(
                val tagId: String,
                val title: String
            )
        }

        class Category(
            val categoryId: String,
            val title: String,
            val avatarUrl: String
        )
    }
}
