package com.debbly.server.stages

import com.debbly.server.IdGenerator
import com.debbly.server.stages.model.*
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StageService(
    private val stageRepository: StageRepository,
    private val stageHostRepository: StageHostRepository,
    private val liveStageRepository: LiveStageRepository,
    private val idGenerator: IdGenerator
) {

    fun createStage(type: StageType, claimId: String?, userId: String): StageEntity {
        val stageId = idGenerator.id()
        val stage = StageEntity(stageId, type, claimId, Instant.now(), null)
        stageRepository.save(stage)
        val stageHost = StageHostEntity(stageId, userId)
        stageHostRepository.save(stageHost)
        return stage
    }

    fun leaveStage(stageId: String, userId: String) {
        val stageHost = stageHostRepository.findByStageIdAndUserId(stageId, userId)
        if (stageHost != null) {
            stageHostRepository.delete(stageHost)
            val hosts = stageHostRepository.findByStageId(stageId)
            if (hosts.isEmpty()) {
                val stage = stageRepository.findById(stageId).orElseThrow()
                stage.copy(closedAt = Instant.now())
                stageRepository.save(stage)
                liveStageRepository.deleteById(stageId)
            }
        }
    }

    fun live(stageId: String, userId: String) {
        val liveStage = LiveStageEntity(stageId, Instant.now())
        liveStageRepository.save(liveStage)
    }

    fun heartbeat(stageId: String, userId: String) {
        val liveStage = liveStageRepository.findById(stageId).orElseThrow()
        liveStage.copy(heartbeatAt = Instant.now())
        liveStageRepository.save(liveStage)
    }
}