package com.debbly.server.auth

import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
) {
    fun authenticate(externalUserId: String?) =
        externalUserId?.let { userRepository.getByExternalUserId(externalUserId) } ?: throw UnauthorizedException()
}