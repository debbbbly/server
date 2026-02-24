package com.debbly.server.event.repository

import java.io.Serializable

data class EventUserId(
    val eventId: String = "",
    val userId: String = ""
) : Serializable
