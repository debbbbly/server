package com.debbly.server.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.time.temporal.ChronoUnit

@Configuration
class JsonConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        
        // Configure JavaTimeModule with millisecond precision
        val javaTimeModule = JavaTimeModule()
        
        // Custom Instant serializer that truncates to milliseconds
        javaTimeModule.addSerializer(Instant::class.java, object : InstantSerializer() {
            override fun createContextual(prov: com.fasterxml.jackson.databind.SerializerProvider?, 
                                        property: com.fasterxml.jackson.databind.BeanProperty?): com.fasterxml.jackson.databind.JsonSerializer<*> {
                return InstantSerializer.INSTANCE.createContextual(prov, property)
            }
            
            override fun serialize(value: Instant?, gen: com.fasterxml.jackson.core.JsonGenerator?, serializers: com.fasterxml.jackson.databind.SerializerProvider?) {
                if (value != null) {
                    // Truncate to milliseconds before serialization
                    val truncated = value.truncatedTo(ChronoUnit.MILLIS)
                    super.serialize(truncated, gen, serializers)
                } else {
                    super.serialize(value, gen, serializers)
                }
            }
        })
        
        return mapper
            .registerKotlinModule()
            .registerModule(javaTimeModule)
            .registerModule(ParameterNamesModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}