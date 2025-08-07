package com.debbly.server.infra.error

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(ex: UnauthorizedException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(ex.message ?: "Unauthorized")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }

    data class ErrorResponse(val message: String)
}
