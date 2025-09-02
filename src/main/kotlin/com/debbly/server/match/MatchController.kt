package com.debbly.server.match

import com.debbly.server.auth.AuthService
import com.debbly.server.auth.ExternalUserId
import com.debbly.server.match.MatchService.MatchingState
import com.debbly.server.match.model.Match
import com.debbly.server.match.model.MatchRequest
import com.debbly.server.infra.error.ForbiddenException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/match")
class MatchController(
    private val matchService: MatchService,
    private val authService: AuthService,
) {

    @PostMapping("/join")
    fun joinQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            matchService.join(user)
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/leave")
    fun leaveQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        authService.authenticate(externalUserId).let { user ->
            matchService.leave(user)
        }
        return ResponseEntity.ok().build()
    }

    // TODO: remove ??? looks like a backdoor
    @GetMapping("/queue")
    fun getQueue(): ResponseEntity<GetQueueResponse> {
        return ResponseEntity.ok(GetQueueResponse(matchService.getQueue()))
    }

    data class GetQueueResponse(
        val users: List<MatchRequest>
    )

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

    // TODO: remove ??? looks like a backdoor
    @PostMapping("/match")
    fun match(): ResponseEntity<Void> {
        matchService.runMatching()
        return ResponseEntity.ok().build()
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