package com.debbly.server.match

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.auth.service.AuthService
import com.debbly.server.home.model.QueueResponse
import com.debbly.server.infra.error.ForbiddenException
import com.debbly.server.match.MatchService.MatchingState
import com.debbly.server.match.model.JoinMatchRequest
import com.debbly.server.match.model.LeaveMatchRequest
import com.debbly.server.match.model.Match
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/match")
class MatchController(
    private val matchService: MatchService,
    private val authService: AuthService,
) {

    @PostMapping("/join")
    fun joinQueue(
        @ExternalUserId externalUserId: String?,
        @RequestBody request: JoinMatchRequest
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            matchService.join(user, request)
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/leave")
    fun leaveQueue(
        @ExternalUserId externalUserId: String?,
        @RequestBody(required = false) request: LeaveMatchRequest?
    ): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            matchService.leave(user, request?.claimIds)
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/queue")
    fun getQueue(@ExternalUserId externalUserId: String?): ResponseEntity<QueueResponse> {
        val user = authService.authenticate(externalUserId)
        return ResponseEntity.ok(matchService.getQueueDetails(user.userId))
    }

    // TODO: remove ??? looks like a backdoor
    @GetMapping("/all")
    fun findAllMatches(): ResponseEntity<FindAllMatchesResponse> {
        return ResponseEntity.ok(FindAllMatchesResponse(matchService.findAllMatches()))
    }

    data class FindAllMatchesResponse(
        val users: List<Match>
    )

    @GetMapping("/state")
    fun getMatchingState(@ExternalUserId externalUserId: String?): ResponseEntity<MatchingState> {

        val state = authService.authenticate(externalUserId).let { user ->
            matchService.getMatchingState(user)
        }
        return ResponseEntity.ok(state)
    }

    @PostMapping("/{matchId}/skip")
    fun skip(
        @ExternalUserId externalUserId: String?,
        @PathVariable matchId: String
    ): ResponseEntity<Void> {
        authorize(externalUserId, matchId)
            .let { (user, match) -> matchService.skip(match, user) }

        return ResponseEntity.ok().build()
    }

    @PostMapping("/{matchId}/accept")
    fun accept(
        @ExternalUserId externalUserId: String?,
        @PathVariable matchId: String
    ): ResponseEntity<Void> {
        authorize(externalUserId, matchId)
            .let { (user, match) -> matchService.accept(match, user) }

        //val stageDetails = stageService.getStageDetails(stage.stageId, user.userId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{matchId}/switch")
    fun switch(
        @ExternalUserId externalUserId: String?,
        @PathVariable matchId: String
    ): ResponseEntity<Void> {
        authorize(externalUserId, matchId)
            .let { (user, match) -> matchService.switch(match, user) }

        return ResponseEntity.ok().build()
    }

    private fun authorize(externalUserId: String?, matchId: String) =
        authService.authenticate(externalUserId).let { user ->
            val match = matchService.getMatch(user.userId)
                ?.takeIf { it.matchId == matchId }
                ?: throw ForbiddenException()
            user to match
        }
}