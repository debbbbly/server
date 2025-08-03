package com.debbly.server

import com.debbly.server.auth.config.CognitoConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(CognitoConfig::class)
class ServerApplication

fun main(args: Array<String>) {
    runApplication<ServerApplication>(*args)
}
