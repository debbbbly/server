package com.debbly.server.config

import com.debbly.server.livekit.LiveKitService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class StartupHealthCheck(
    private val redisTemplate: RedisTemplate<String, Any>?,
    private val liveKitService: LiveKitService,
    private val authConfig: AuthConfigProperties,
    private val restTemplate: RestTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun checkDependencies() {
        checkRedis()
        checkLiveKit()
        checkSupabaseAuth()
    }

    private fun checkRedis() {
        try {
            redisTemplate?.connectionFactory?.connection?.ping()
            logger.info("✓ Redis connection successful")
        } catch (e: Exception) {
            logger.error("✗ Redis connection failed: ${e.message}")
        }
    }

    private fun checkLiveKit() {
        try {
            // Simple lightweight call to verify LiveKit API is accessible
            liveKitService.listAllEgresses()
            logger.info("✓ LiveKit connection successful")
        } catch (e: Exception) {
            logger.error("✗ LiveKit connection failed: ${e.message}")
        }
    }

    private fun checkSupabaseAuth() {
        try {
            // Try to reach the auth health endpoint
            val healthUrl = "${authConfig.url}/health"
            val response = restTemplate.getForEntity(healthUrl, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                logger.info("✓ Supabase Auth (GoTrue) connection successful")
            } else {
                logger.warn("✗ Supabase Auth responded with status: ${response.statusCode}")
            }
        } catch (e: Exception) {
            logger.error("✗ Supabase Auth connection failed: ${e.message}")
        }
    }
}