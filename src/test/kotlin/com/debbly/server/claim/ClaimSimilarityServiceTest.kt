package com.debbly.server.claim

import com.debbly.server.embedding.EmbeddingService
import com.debbly.server.embedding.claim.ClaimEmbeddingRepository
import com.debbly.server.embedding.claim.SimilarClaimProjection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClaimSimilarityServiceTest {

    private val embeddingRepo: ClaimEmbeddingRepository = mock()
    private val embeddingService: EmbeddingService = mock()
    private val service = ClaimSimilarityService(embeddingRepo, embeddingService)

    @Test
    fun `findSimilarClaims returns empty when embedding generation fails`() {
        whenever(embeddingService.generateEmbedding("text")).thenReturn(null)
        assertEquals(emptyList<SimilarClaim>(), service.findSimilarClaims("text"))
    }

    @Test
    fun `findSimilarClaimsByEmbedding maps projections and flags duplicates`() {
        whenever(embeddingRepo.findSimilarByEmbedding(any(), any(), any())).thenReturn(
            listOf(
                SimilarClaimProjection("c1", "t1", "cat1", 0.96),
                SimilarClaimProjection("c2", "t2", "cat1", 0.80)
            )
        )

        val result = service.findSimilarClaimsByEmbedding(listOf(0.1, 0.2))

        assertEquals(2, result.size)
        assertTrue(result[0].isDuplicate)
        assertEquals("c1", result[0].claimId)
        assertFalse(result[1].isDuplicate)
    }

    @Test
    fun `findSimilarClaimsByEmbedding returns empty on exception`() {
        whenever(embeddingRepo.findSimilarByEmbedding(any(), any(), any()))
            .thenThrow(RuntimeException("boom"))
        assertEquals(emptyList<SimilarClaim>(), service.findSimilarClaimsByEmbedding(listOf(0.1)))
    }

    @Test
    fun `findDuplicate returns null when no similar claim above 0_95`() {
        whenever(embeddingService.generateEmbedding("text")).thenReturn(listOf(0.1))
        whenever(embeddingRepo.findSimilarByEmbedding(any(), eq(0.95), eq(1))).thenReturn(emptyList())
        assertNull(service.findDuplicate("text"))
    }

    @Test
    fun `findDuplicate returns claim when similarity is at least 0_95`() {
        whenever(embeddingService.generateEmbedding("text")).thenReturn(listOf(0.1))
        whenever(embeddingRepo.findSimilarByEmbedding(any(), eq(0.95), eq(1)))
            .thenReturn(listOf(SimilarClaimProjection("c1", "t1", "cat1", 0.97)))

        val dup = service.findDuplicate("text")

        assertEquals("c1", dup?.claimId)
        assertTrue(dup?.isDuplicate == true)
    }
}
