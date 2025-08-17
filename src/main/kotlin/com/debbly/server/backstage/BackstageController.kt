package com.debbly.server.backstage

import com.debbly.server.auth.ExternalUserId
import com.debbly.server.user.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/backstage")
class BackstageController(
    private val service: BackstageService,
    private val userRepository: UserRepository
) {

    @PostMapping("/join")
    fun joinQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        val user =
            externalUserId?.let { userRepository.findByExternalUserId(externalUserId) } ?: return ResponseEntity.status(
                401
            ).build()

        service.join(user)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/leave")
    fun leaveQueue(@ExternalUserId externalUserId: String?): ResponseEntity<Void> {
        val user =
            externalUserId?.let { userRepository.findByExternalUserId(externalUserId) } ?: return ResponseEntity.status(
                401
            ).build()

        service.leave(user)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/queue")
    fun getQueue(): ResponseEntity<GetQueueResponse> {
        return ResponseEntity.ok(GetQueueResponse(service.getQueue()))
    }

    data class GetQueueResponse(
        val users: List<BackstageHost>
    )

    data class UserInfo(
        val userId: String,
        val username: String?,
        val avatarUrl: String?
    )

    @GetMapping("/status")
    fun getMatchStatus(@ExternalUserId externalUserId: String?): ResponseEntity<GetMatchStatusResponse> {
        if (externalUserId == null) {
            return ResponseEntity.status(401).build()
        }
        val matches = service.getMatchStatus(externalUserId)
        return ResponseEntity.ok(GetMatchStatusResponse(matches))
    }

    data class GetMatchStatusResponse(
        val matches: List<BackstageMatch>
    )

    @PostMapping("/match")
    fun match(): ResponseEntity<Void> {
        service.performMatching()
        return ResponseEntity.ok().build()
    }
}
