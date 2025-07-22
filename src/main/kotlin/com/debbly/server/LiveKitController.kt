package com.debbly.server

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class LiveKitController(private val liveKitService: LiveKitService) {

    @GetMapping("/livekit/token")
    fun getToken(@RequestParam userId: String, @RequestParam roomName: String): String {
        return liveKitService.getToken(userId, roomName)
    }
}