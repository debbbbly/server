package com.debbly.server.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder

@Configuration
class JsonConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()

        // Configure JavaTimeModule with millisecond precision
        val javaTimeModule = JavaTimeModule()

        // Custom Instant serializer with exactly 3 decimal places (milliseconds)
        javaTimeModule.addSerializer(Instant::class.java, MillisInstantSerializer())

        return mapper
            .registerKotlinModule()
            .registerModule(javaTimeModule)
            .registerModule(ParameterNamesModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}

class MillisInstantSerializer : StdSerializer<Instant>(Instant::class.java) {

    companion object {
        private val FORMATTER = DateTimeFormatterBuilder()
            .appendInstant(3) // Exactly 3 decimal places for milliseconds
            .toFormatter()
    }

    override fun serialize(value: Instant?, gen: JsonGenerator, provider: SerializerProvider) {
        if (value == null) {
            gen.writeNull()
        } else {
            gen.writeString(FORMATTER.format(value.atOffset(ZoneOffset.UTC)))
        }
    }
}