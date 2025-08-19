package com.debbly.server.backstage

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.backstage.model.Match
import com.debbly.server.claim.UserClaimStanceService
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.stage.StageService
import com.debbly.server.user.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/backstage")
class BackstageController(
    private val backstageService: BackstageService,
    private val userRepository: UserRepository,
    private val authService: AuthService,
    private val stageService: StageService,
    private val userClaimStanceService: UserClaimStanceService
) {

    @PostMapping("/join")
    fun joinQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            backstageService.join(user)
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/leave")
    fun leaveQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            backstageService.leave(user)
        }
        return ResponseEntity.ok().build()
    }

    // TODO: remove ??? looks like a backdoor
    @GetMapping("/queue")
    fun getQueue(): ResponseEntity<GetQueueResponse> {
        return ResponseEntity.ok(GetQueueResponse(backstageService.getQueue()))
    }

    data class GetQueueResponse(
        val users: List<MatchRequest>
    )

    @GetMapping("/status")
    fun getMatchStatus(@ExternalUserId externalUserId: String?): ResponseEntity<GetMatchStatusResponse> {
        val matches = authService.authenticate(externalUserId).let { user ->
            backstageService.getMatchStatus(user)
        }
        return ResponseEntity.ok(GetMatchStatusResponse(matches))
    }

    data class GetMatchStatusResponse(
        val matches: List<Match>
    )

    // TODO: remove ??? looks like a backdoor
    @PostMapping("/match")
    fun match(): ResponseEntity<Void> {
        backstageService.performMatching()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/match/{matchId}/skip")
    fun skip(
        @ExternalUserId externalUserId: String?,
        @PathVariable matchId: String
    ): ResponseEntity<Void> {
        authorize(externalUserId, matchId)
            .let { (user, match) -> backstageService.skip(match, user) }
        return ResponseEntity.ok().build()
    }


    @PostMapping("/match/{matchId}/accept")
    fun accept(
        @ExternalUserId externalUserId: String?,
        @PathVariable matchId: String
    ): ResponseEntity<Void> {
        authorize(externalUserId, matchId)
            .let { (user, match) -> backstageService.accept(match, user) }

        //val stageDetails = stageService.getStageDetails(stage.stageId, user.userId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/match/{matchId}/switch")
    fun switch(
        @ExternalUserId externalUserId: String?,
        @PathVariable matchId: String
    ): ResponseEntity<Void> {
        authorize(externalUserId, matchId)
            .let { (user, match) -> backstageService.switch(match, user) }

        return ResponseEntity.ok().build()
    }

    private fun authorize(externalUserId: String?, matchId: String) =
        authService.authenticate(externalUserId).let { user ->
            val match = backstageService.getMatch(user.userId)
                ?.takeIf { it.matchId == matchId }
                ?: throw ForbiddenException()
            user to match
        }
}