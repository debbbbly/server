package com.debbly.server.stages

import com.debbly.server.IdService
import com.debbly.server.stages.model.*
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StageService(
    private val stageRepository: StageRepository,
    private val stageHostRepository: StageHostRepository,
    // private val liveStageRepository: LiveStageRepository,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val idGenerator: IdService
) {

    fun createStage(type: StageType, claimId: String?, userId: String): StageEntity {
        val stageId = idGenerator.id()
        val stage = StageEntity(stageId, type, claimId, Instant.now(), null)
        stageRepository.save(stage)
        val stageHost = StageHostEntity(StageHostId(stageId, userId))
        stageHostRepository.save(stageHost)
        return stage
    }

    fun live(stageId: String, userId: String) {


        val stage = stageRepository.findById(stageId).orElseThrow()
        val liveStageRedisDto = LiveStageRedisDto(stageId, stage.type, stage.claimId, Instant.now())
        liveStageRedisRepository.save(liveStageRedisDto)
    }

    fun heartbeat(stageId: String, userId: String) {
        val liveStageRedisDto = liveStageRedisRepository.findById(stageId).orElseThrow()
        liveStageRedisDto.heartbeatAt = Instant.now()
        liveStageRedisRepository.save(liveStageRedisDto)
    }

    fun leaveStage(stageId: String, userId: String) {
        val stageHostId = StageHostId(stageId, userId)
        val stageHost = stageHostRepository.findById(stageHostId)
        if (stageHost.isPresent) {
            stageHostRepository.delete(stageHost.get())
            val hosts = stageHostRepository.findByIdStageId(stageId)
            if (hosts.isEmpty()) {
                val stage = stageRepository.findById(stageId).orElseThrow()
                stageRepository.save(stage.copy(closedAt = Instant.now()))
                //liveStageRepository.deleteById(stageId)
                liveStageRedisRepository.deleteById(stageId)
            }
        }
    }
}
