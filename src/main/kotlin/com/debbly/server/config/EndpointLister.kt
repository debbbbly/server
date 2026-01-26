package com.debbly.server.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@Component
class EndpointLister(private val requestMappingHandlerMapping: RequestMappingHandlerMapping) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun listEndpoints() {
        logger.info("=== REGISTERED ENDPOINTS ===")
        requestMappingHandlerMapping.handlerMethods
            .toSortedMap(compareBy { it.toString() })
            .forEach { (mapping, method) ->
                logger.info("REGISTERED_ENDPOINT: $mapping -> ${method.beanType.simpleName}.${method.method.name}")
            }
        logger.info("=== END REGISTERED ENDPOINTS ===")
    }
}
