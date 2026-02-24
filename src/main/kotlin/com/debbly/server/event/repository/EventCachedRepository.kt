package com.debbly.server.event.repository

import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class EventCachedRepository(private val eventJpaRepository: EventJpaRepository) {

    @Cacheable(value = ["events"], key = "#eventId", unless = "#result == null")
    fun findById(eventId: String): EventEntity? =
        eventJpaRepository.findById(eventId).getOrNull()

    @CacheEvict(value = ["events"], key = "#event.eventId")
    fun save(event: EventEntity): EventEntity = eventJpaRepository.save(event)
}
