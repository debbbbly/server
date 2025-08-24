package com.debbly.server.auth

import com.debbly.server.infra.error.UnauthorizedException
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userCachedRepository: UserCachedRepository,
) {
    fun authenticate(externalUserId: String?) =
        externalUserId?.let { 
            userCachedRepository.findByExternalUserId(externalUserId) ?: throw UnauthorizedException()
        } ?: throw UnauthorizedException()
}