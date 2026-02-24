package com.debbly.server.event.model

enum class EventStatus {
    SCHEDULED,
    LIVE,
    COMPLETED,
    CANCELLED
}

enum class EventAcceptanceStatus {
    SIGNED_UP,
    MATCHED,
    REMOVED,
    WITHDRAWN,
    NO_SHOW
}

enum class EventListFilter {
    UPCOMING,
    LIVE,
    PAST
}
