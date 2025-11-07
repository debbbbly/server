package com.debbly.server.pusher.config

import com.pusher.rest.Pusher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PusherConfig(
    private val pusherProperties: PusherProperties
) {

    @Bean
    fun pusher(): Pusher {
        val pusher = Pusher(
            pusherProperties.appId,
            pusherProperties.key,
            pusherProperties.secret
        )
        pusher.setCluster(pusherProperties.cluster)
        pusher.setEncrypted(true)
        return pusher
    }
}