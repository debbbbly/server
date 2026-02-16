package com.debbly.server.pusher.controller

import com.debbly.server.match.MatchService
import com.debbly.server.match.QueueService
import com.debbly.server.pusher.config.PusherProperties
import com.debbly.server.pusher.service.PusherService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/pusher")
class PusherWebhookController(
    private val matchService: MatchService,
    private val queueService: QueueService,
    private val pusherProperties: PusherProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestHeader("X-Pusher-Key") pusherKey: String,
        @RequestHeader("X-Pusher-Signature") signature: String,
        @RequestBody body: String
    ): ResponseEntity<Void> {
        if (pusherKey != pusherProperties.key) {
            logger.warn("Invalid Pusher key in webhook: {}", pusherKey)
            return ResponseEntity.status(401).build()
        }

        if (!verifySignature(body, signature)) {
            logger.warn("Invalid Pusher webhook signature")
            return ResponseEntity.status(401).build()
        }

        try {
            val webhook = objectMapper.readTree(body)
            val events = webhook.get("events") ?: return ResponseEntity.ok().build()

            for (event in events) {
                val eventName = event.get("name")?.asText() ?: continue
                val channel = event.get("channel")?.asText() ?: continue

                if (eventName == "channel_vacated" && channel.startsWith(PusherService.USER_CHANNEL_PREFIX)) {
                    val userId = channel.removePrefix(PusherService.USER_CHANNEL_PREFIX)
                    logger.info("User {} disconnected (channel_vacated), removing from queue", userId)
                    matchService.removeFromQueue(userId)
                    queueService.broadcastQueueUpdate()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process Pusher webhook", e)
        }

        return ResponseEntity.ok().build()
    }

    private fun verifySignature(body: String, signature: String): Boolean {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(pusherProperties.secret.toByteArray(), "HmacSHA256"))
            val expectedSignature = mac.doFinal(body.toByteArray())
                .joinToString("") { "%02x".format(it) }
            expectedSignature == signature
        } catch (e: Exception) {
            logger.error("Failed to verify webhook signature", e)
            false
        }
    }
}
