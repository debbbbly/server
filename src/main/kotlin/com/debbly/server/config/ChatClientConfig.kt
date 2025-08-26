package com.debbly.server.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChatClientConfig {

    @Bean
    fun chatClient(builder: ChatClient.Builder): ChatClient {
        return builder.build()
    }
}