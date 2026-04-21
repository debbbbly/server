package com.debbly.server.embedding

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class EmbeddingService(
    @Value("\${spring.ai.openai.api-key}") private val openaiApiKey: String,
    private val restTemplate: RestTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val embeddingUrl = "https://api.openai.com/v1/embeddings"

    fun generateEmbedding(text: String): List<Double>? {
        return try {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            headers.setBearerAuth(openaiApiKey)

            val requestBody = mapOf(
                "model" to "text-embedding-3-small",
                "input" to text
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForObject(embeddingUrl, request, EmbeddingResponse::class.java)
            response?.data?.firstOrNull()?.embedding
        } catch (e: Exception) {
            logger.error("Error generating embedding: ${e.message}", e)
            null
        }
    }
}

data class EmbeddingResponse(
    val `object`: String,
    val data: List<EmbeddingData>,
    val model: String,
    val usage: EmbeddingUsage
)

data class EmbeddingData(
    val `object`: String,
    val embedding: List<Double>,
    val index: Int
)

data class EmbeddingUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @JsonProperty("total_tokens")
    val totalTokens: Int
)
