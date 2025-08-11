package com.debbly.server.match

import com.debbly.server.auth.ExternalUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/match")
class MatchQueueController(private val service: MatchQueueService) {

    @PostMapping("/join")
    fun joinQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        if (externalUserId == null) {
            return ResponseEntity.status(401).build()
        }
        service.joinQueue(externalUserId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/leave")
    fun leaveQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        if (externalUserId == null) {
            return ResponseEntity.status(401).build()
        }
        service.leaveQueue(externalUserId)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/status")
    fun getMatchStatus(@ExternalUserId externalUserId: String?): ResponseEntity<GetMatchStatusResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(401).build()
        }
        val matches = service.getMatchStatus(externalUserId)
        return ResponseEntity.ok(GetMatchStatusResponse(matches))
    }

    data class GetMatchStatusResponse(
        val matches: List<MatchResult>
    )

    @PostMapping
    fun match(): ResponseEntity<Void> {
        service.performMatching()
        return ResponseEntity.ok().build()
    }
}
