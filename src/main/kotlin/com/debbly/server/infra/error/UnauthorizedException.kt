package com.debbly.server.infra.error

class UnauthorizedException(message: String? = "Unauthorized") : RuntimeException(message)
