package com.debbly.server.stage

import com.debbly.server.IdService
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.stage.model.*
import com.debbly.server.user.UserService
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StageService(
    private val stageRepository: StageRepository,
    private val stageHostRepository: StageHostRepository,
    // private val liveStageRepository: LiveStageRepository,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val userService: UserService,
    private val idService: IdService
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
            stageRepository.save(
                StageEntity(
                    stageId = idService.getId(),
                    type = StageType.SOLO,
                    hosts = setOf(
                        StageHostEntity(
                            StageHostId(
                                stageId = stageId,
                                userId = userId
                            )
                        )
                    ),
                    topic = null,
                    createdAt = Instant.now()
                )
            )
        } else {
            stageRepository.findById(stageId)
                .orElseThrow()
                .also { stage ->
                    if (stage.hosts.none { it.id.userId == userId }) {
                        throw UnauthorizedException("User is not a host of this stage")
                    }
                }
        }

        val users = stage.hosts.mapNotNull { userService.findById(it.id.userId) }.associateBy { it.userId }

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
        val stage = stageRepository.findById(stageId).orElseThrow()
        if (stage.hosts.none { it.id.userId == userId }) {
            throw UnauthorizedException("User is not a host of this stage")
        }
        stageRepository.save(stage.copy(closedAt = Instant.now()))
        liveStageRedisRepository.deleteById(stageId)
    }
}
