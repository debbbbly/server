package com.debbly.server

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RoomController(private val liveKitService: LiveKitService) {

    @GetMapping("/livekit/token")
    fun getToken(@RequestParam userId: String, @RequestParam roomName: String): String {
        return "Token: ${liveKitService.getToken(userId, roomName)}"
    }

    @GetMapping("/rooms")
    fun find(@RequestParam userId: String, @RequestParam roomId: String): Room {
        return Room(token = liveKitService.getToken(userId, roomId))
    }

    data class Room(
        val token: String,
    )
}