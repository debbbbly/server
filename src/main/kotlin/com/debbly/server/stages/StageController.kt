package com.debbly.server.stages

import com.debbly.server.stages.model.StageEntity
import com.debbly.server.stages.model.StageType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/stages")
class StageController(private val stageService: StageService) {

    @PostMapping
    fun createStage(
        @RequestBody request: CreateStageRequest,
        @AuthenticationPrincipal jwt: Jwt?
    ): ResponseEntity<StageEntity> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val userId = jwt.claims["sub"] as String

        val stage = stageService.createStage(
            type = request.type,
            claimId = request.claimId,
            userId = userId
        )

        return ResponseEntity.ok(stage)
    }

    @PostMapping("/{stageId}/live")
    fun live(@PathVariable stageId: String, @AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Unit> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val userId = jwt.claims["sub"] as String

        stageService.live(
            stageId = stageId,
            userId = userId
        )

        return ResponseEntity.ok().build()
    }

    @PostMapping("/{stageId}/heartbeat")
    fun heartbeat(@PathVariable stageId: String, @AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Unit> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val userId = jwt.claims["sub"] as String

        stageService.heartbeat(
            stageId = stageId,
            userId = userId
        )

        return ResponseEntity.ok().build()
    }

    @PostMapping("/{stageId}/leave")
    fun leave(@PathVariable stageId: String, @AuthenticationPrincipal jwt: Jwt?): ResponseEntity<Unit> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val userId = jwt.claims["sub"] as String

        stageService.leaveStage(
            stageId = stageId,
            userId = userId
        )

        return ResponseEntity.ok().build()
    }
}

data class CreateStageRequest(
    val type: StageType,
    val claimId: String?
)
