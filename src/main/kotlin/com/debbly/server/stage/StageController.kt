package com.debbly.server.stage

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.stage.model.LiveStageEntity
import com.debbly.server.stage.model.LiveStageRedisRepository
import com.debbly.server.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stages")
class StageController(
    private val stageService: StageService,
    private val liveStageRedisRepository: LiveStageRedisRepository,
    private val userService: UserService
) {

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

        userService.findByExternalUserId(externalUserId)?.let { user ->
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

    @PostMapping("/{stageId}/heartbeat")
    fun heartbeat(@PathVariable stageId: String, @ExternalUserId externalUserId: String?): ResponseEntity<Unit> {
        if (externalUserId == null) {
            throw UnauthorizedException()
        }

        userService.findByExternalUserId(externalUserId)?.let { user ->
            stageService.heartbeat(
                stageId = stageId,
                userId = user.userId
            )
        }

        return ResponseEntity.ok().build()
    }

    @PostMapping("/{stageId}/leave")
    fun leave(@PathVariable stageId: String, @ExternalUserId externalUserId: String?): ResponseEntity<Unit> {
        if (externalUserId == null) {
            throw UnauthorizedException()
        }

        userService.findByExternalUserId(externalUserId)?.let { user ->
            stageService.leaveStage(
                stageId = stageId,
                userId = user.userId
            )
        }

        return ResponseEntity.ok().build()
    }
}
