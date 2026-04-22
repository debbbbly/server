package com.debbly.server.infra.error

import com.debbly.server.claim.SimilarClaim
import com.debbly.server.claim.exception.ClaimValidationException
import com.debbly.server.claim.exception.DuplicateClaimException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MaxUploadSizeExceededException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `handles ForbiddenException with 403`() {
        val response = handler.handleForbiddenException(ForbiddenException("nope"))
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("nope", response.body?.message)
    }

    @Test
    fun `handles UnauthorizedException with 401`() {
        val response = handler.handleUnauthorizedException(UnauthorizedException())
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("Unauthorized", response.body?.message)
    }

    @Test
    fun `handles ClaimValidationException with 400`() {
        val ex = ClaimValidationException(listOf("bad"), "reason")
        val response = handler.handleClaimValidationException(ex)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `handles DuplicateClaimException with 409`() {
        val duplicate = SimilarClaim(
            claimId = "c1",
            claimSlug = null,
            title = "t",
            categoryId = "cat",
            similarity = 0.99,
            isDuplicate = true
        )
        val response = handler.handleDuplicateClaimException(DuplicateClaimException(duplicate))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `handles MaxUploadSizeExceededException with 413`() {
        val response = handler.handleMaxSizeException(MaxUploadSizeExceededException(5_000_000L))
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.statusCode)
        assertEquals("File size exceeds maximum allowed size of 5MB", response.body?.message)
    }

    @Test
    fun `handles generic Exception with 500`() {
        val response = handler.handleGenericException(RuntimeException("boom"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("An unexpected error occurred", response.body?.message)
    }
}
