package com.debbly.server.livekit

import com.debbly.server.config.LiveKitConfig
import io.livekit.server.*;
import livekit.LivekitWebhook
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/livekit")
class LiveKitController(
    liveKitConfig: LiveKitConfig,
    private val liveKitWebhookService: LiveKitWebhookService
) {
    private val webhookReceiver = WebhookReceiver(liveKitConfig.apiKey, liveKitConfig.apiSecret)
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook", consumes = ["application/webhook+json"])
    fun handleLiveKitWebhook(
        @RequestBody postBody: String,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<Void> {
        val event: LivekitWebhook.WebhookEvent = webhookReceiver.receive(postBody, authHeader)

//        logger.info("Received livekit webhook event: ${event.event}")

        liveKitWebhookService.processWebhookEvent(event)

        return ResponseEntity.ok().build()
    }
}