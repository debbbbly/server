package com.debbly.server.stage.event

/**
 * Event published when all hosts have joined a stage.
 */
data class AllHostsJoinedEvent(
    val stageId: String
)