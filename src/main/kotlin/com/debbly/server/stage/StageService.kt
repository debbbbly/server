package com.debbly.server.stage

import com.debbly.server.IdService
import com.debbly.server.LiveKitService
import com.debbly.server.claim.ClaimRepository
import com.debbly.server.claim.ClaimStance
import com.debbly.server.claim.ClaimStanceRepository
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.stage.model.*
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.stage.repository.StageCachedRepository
import com.debbly.server.stage.repository.StageHostRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class StageService(
    private val stageCachedRepository: StageCachedRepository,
    private val stageHostRepository: StageHostRepository,
    // private val liveStageRepository: LiveStageRepository,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val userCachedRepository: UserCachedRepository,
    private val idService: IdService,
    private val claimRepository: ClaimRepository,
    private val claimStanceRepository: ClaimStanceRepository,
    private val liveKitService: LiveKitService
) {

//    fun createStage(type: StageType, claimId: String?, userId: String): StageEntity {
//        val stageId = idService.getId()
//        val stage = StageEntity(stageId, type, claimId, Instant.now(), null)
//        stageRepository.save(stage)
//        val stageHost = StageHostEntity(StageHostId(stageId, userId))
//        stageHostRepository.save(stageHost)
//        return stage
//    }


    fun live(stageId: String, userId: String): StageEntity {
        val stage = if (stageId == userId) {
            stageCachedRepository.save(
                StageEntity(
                    stageId = idService.getId(),
                    type = StageType.SOLO,
                    hosts = setOf(
                        StageHostEntity(
                            StageHostId(
                                stageId = stageId,
                                userId = userId,
                            ),
                            stance = null
                        )
                    ),
                    topic = null,
                    claim = null,
                    createdAt = Instant.now()
                )
            )
        } else {
            stageCachedRepository.findById(stageId)

                ?.also { stage ->
                    if (stage.hosts.none { it.id.userId == userId }) {
                        throw UnauthorizedException("User is not a host of this stage")
                    }
                } ?: throw UnauthorizedException("Stage not found")
        }

        val users = stage.hosts.mapNotNull { userCachedRepository.findById(it.id.userId) }.associateBy { it.userId }

        liveStageRedisRepository.save(
            LiveStageEntity(
                stageId = stageId,
                type = stage.type,
                hosts = stage.hosts.mapNotNull { it ->
                    users[it.id.userId]?.let { user ->
                        LiveStageHost(
                            user.userId,
                            user.username ?: "unknown"
                        )
                    }
                },
                claimId = stage.topic,
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
        stageCachedRepository.findById(stageId)?.let { stage ->
            if (stage.hosts.none { it.id.userId == userId }) {
                throw UnauthorizedException("User is not a host of this stage")
            }
            stageCachedRepository.save(stage.copy(closedAt = Instant.now()))
            liveStageRedisRepository.deleteById(stageId)
        }
    }

    @Transactional(readOnly = true)
    fun getStageDetails(stageId: String, userId: String?): StageDetails {
        val stage = stageCachedRepository.getById(stageId)
        val claim = stage.claim
        val hosts = stage.hosts.map { host ->
            val user = userCachedRepository.findById(host.id.userId) ?: throw Exception("User not found")
            val stance = host.stance

            StageDetails.Host(
                userId = user.userId,
                username = user.username ?: "unknown",
                avatarUrl = user.avatarUrl,
                stance = stance ?: ClaimStance.ANY
            )
        }

        val isHost = userId?.let { stage.hosts.any { it.id.userId == userId } } ?: false
        val livekitToken = userId?.takeIf { isHost }?.let {
            liveKitService.getToken(userId = userId, stageId = stageId)
        }

        return StageDetails(
            stageId = stage.stageId,
            claim = stage.claim?.let { claim ->
                StageDetails.Claim(
                    claimId = claim.claimId,
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
