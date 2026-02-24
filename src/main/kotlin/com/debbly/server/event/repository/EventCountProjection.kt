package com.debbly.server.event.repository

interface EventCountProjection {
    fun getEventId(): String
    fun getCount(): Long
}
