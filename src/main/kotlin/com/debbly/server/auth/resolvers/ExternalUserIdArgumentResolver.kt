package com.debbly.server.auth.resolvers

import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class ExternalUserIdArgumentResolver : HandlerMethodArgumentResolver {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.getParameterAnnotation(ExternalUserId::class.java) != null
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val authentication = SecurityContextHolder.getContext().authentication
        logger.debug("Authentication: $authentication")
        val principal = authentication.principal
        logger.debug("Principal type: ${principal?.javaClass?.simpleName}, value: $principal")

        return if (principal is Jwt) {
            val subject = principal.subject
            logger.debug("Extracted subject from JWT: $subject")
            subject
        } else {
            logger.debug("Principal is not a JWT, returning null")
            null
        }
    }
}