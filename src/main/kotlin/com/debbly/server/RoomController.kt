package com.debbly.server

import com.debbly.server.livekit.LiveKitService
import org.springframework.web.bind.annotation.RestController

@RestController
class RoomController(private val liveKitService: LiveKitService) {

//    @GetMapping("/livekit/token")
//    fun getToken(@RequestParam userId: String, @RequestParam roomName: String, @RequestParam isHost: Boolean): String {
//        return "Token: ${liveKitService.getToken(userId, roomName, isHost)}"
//    }
//
//    @GetMapping("/rooms")
//    fun find(@RequestParam userId: String, @RequestParam roomId: String, @RequestParam isHost: Boolean): Room {
//        return Room(token = liveKitService.getToken(userId, roomId, isHost))
//    }

    data class Room(
        val token: String,
    )
}