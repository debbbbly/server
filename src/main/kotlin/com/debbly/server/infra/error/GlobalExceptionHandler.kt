package com.debbly.server.infra.error

import com.debbly.server.claim.exception.ClaimValidationException
import org.springframework.http.HttpStatus.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

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

    data class ErrorResponse(val message: String)
}
