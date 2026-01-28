package com.debbly.server.stage

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.stage.model.LiveStageEntity
import com.debbly.server.stage.repository.LiveStageRedisRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.data.repository.query.Param
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stages")
class StageController(
    private val stageService: StageService,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val userCachedRepository: UserCachedRepository
) {

    @GetMapping("/history")
    fun getUserHostedStages(
        @Param("userId") userId: String
    ): ResponseEntity<List<StageService.StageHistoryDetails>> {
        val stages = stageService.getUserHostedStages(userId)
        return ResponseEntity.ok(stages)
    }

    @GetMapping("/{stageId}")
    fun getStageDetails(
        @PathVariable stageId: String,
        @ExternalUserId externalUserId: String?
    ): ResponseEntity<StageService.StageDetails> {
        val user = externalUserId?.let {
            userCachedRepository.findByExternalUserId(externalUserId) ?: throw UnauthorizedException()
        }
        val stageDetails = stageService.getStageDetails(stageId, user?.userId)
        return ResponseEntity.ok(stageDetails)
    }

    @PostMapping("/{stageId}/heartbeat")
    fun heartbeat(@PathVariable stageId: String, @ExternalUserId externalUserId: String?): ResponseEntity<Unit> {
        if (externalUserId == null) {
            throw UnauthorizedException()
        }

        userCachedRepository.findByExternalUserId(externalUserId)?.let { user ->
            stageService.heartbeat(
                stageId = stageId,
                userId = user.userId
            )
        }

        return ResponseEntity.ok().build()
    }
}
