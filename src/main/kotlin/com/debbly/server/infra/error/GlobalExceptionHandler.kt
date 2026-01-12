package com.debbly.server.infra.error

import com.debbly.server.claim.exception.ClaimValidationException
import com.debbly.server.claim.exception.DuplicateClaimException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(ex.message ?: "Forbidden")
        return ResponseEntity.status(FORBIDDEN).body(errorResponse)
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(ex: UnauthorizedException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(ex.message ?: "Unauthorized")
        return ResponseEntity.status(UNAUTHORIZED).body(errorResponse)
    }

    @ExceptionHandler(ClaimValidationException::class)
    fun handleClaimValidationException(ex: ClaimValidationException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(ex.message ?: "Unknown")
        return ResponseEntity.status(BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(DuplicateClaimException::class)
    fun handleDuplicateClaimException(ex: DuplicateClaimException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(ex.message ?: "Duplicate claim")
        return ResponseEntity.status(CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxSizeException(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ErrorResponse(
                message = "File size exceeds maximum allowed size of 5MB"
            )
        )
    }

    data class ErrorResponse(val message: String)
}
