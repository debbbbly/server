package com.debbly.server.livekit

import com.debbly.server.config.LiveKitConfig
import com.debbly.server.livekit.egress.EgressService
import io.livekit.server.*
import livekit.LivekitWebhook
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/livekit")
class LiveKitController(
    liveKitConfig: LiveKitConfig,
    private val liveKitWebhookService: LiveKitWebhookService,
    private val egressService: EgressService,
) {
    private val webhookReceiver = WebhookReceiver(liveKitConfig.apiKey, liveKitConfig.apiSecret)
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook", consumes = ["application/webhook+json"])
    fun handleLiveKitWebhook(
        @RequestBody postBody: String,
        @RequestHeader("Authorization") authHeader: String,
    ): ResponseEntity<Void> {
        val event: LivekitWebhook.WebhookEvent = webhookReceiver.receive(postBody, authHeader)

//        logger.info("Received livekit webhook event: ${event.event}")

        liveKitWebhookService.processWebhookEvent(event)

        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/egress/{egressId}")
    fun stopEgress(
        @PathVariable egressId: String,
    ): ResponseEntity<Void> {
        val result = egressService.stopCompositeEgress(egressId)

        return if (result.success) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.internalServerError().build()
        }
    }
}
