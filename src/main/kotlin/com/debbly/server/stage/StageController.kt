package com.debbly.server.stage

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.stage.model.LiveStageEntity
import com.debbly.server.stage.model.StageModel
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

    //TODO backdoor for testing purposes
    @PostMapping
    fun createStage( @RequestBody request: CreateStageRequest): ResponseEntity<StageModel> {
        val stage = stageService.createStage(claimId = request.claimId, hosts = request.hosts)

        return ResponseEntity.ok(stage)
    }

    data class CreateStageRequest(
        val claimId: String,
        val hosts: List<StageModel.StageHostModel>
    )

//    @PostMapping
//    fun createStage(
//        @RequestBody request: CreateStageRequest,
//        @AuthenticationPrincipal jwt: Jwt?
//    ): ResponseEntity<StageEntity> {
//        if (jwt == null) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
//        }
//        val userId = jwt.claims["sub"] as String
//
//        val stage = stageService.createStage(
//            type = request.type,
//            claimId = request.claimId,
//            userId = userId
//        )
//
//        return ResponseEntity.ok(stage)
//    }


    @PostMapping("/{stageId}/live")
    fun live(@PathVariable stageId: String, @ExternalUserId externalUserId: String?): ResponseEntity<Unit> {
        if (externalUserId == null) {
            throw UnauthorizedException()
        }

        userCachedRepository.findByExternalUserId(externalUserId)?.let { user ->
            stageService.live(
                stageId = stageId,
                userId = user.userId
            )
        }

        return ResponseEntity.ok().build()
    }

    @GetMapping("/live")
    fun getLiveStages(): ResponseEntity<List<LiveStageEntity>> {
        val liveStages = liveStageRedisRepository.findAll().toList()
        return ResponseEntity.ok(liveStages)
    }

    @GetMapping("/recordings")
    fun getRecordedStages(): ResponseEntity<List<StageService.StageHistoryDetails>> {
        val recordedStages = stageService.getRecordedStages()
        return ResponseEntity.ok(recordedStages)
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

//    @PostMapping("/{stageId}/leave")
//    fun leave(@PathVariable stageId: String, @ExternalUserId externalUserId: String?): ResponseEntity<Unit> {
//        if (externalUserId == null) {
//            throw UnauthorizedException()
//        }
//
//        userCachedRepository.findByExternalUserId(externalUserId)?.let { user ->
//            stageService.leaveStage(
//                stageId = stageId,
//                userId = user.userId
//            )
//        }
//
//        return ResponseEntity.ok().build()
//    }
}
