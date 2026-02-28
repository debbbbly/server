package com.debbly.server.stage.repository.entities

import com.debbly.server.stage.model.StageType
import jakarta.persistence.*
import java.time.Instant

enum class StageStatus {
    PENDING,  // Stage created but not opened yet
    OPEN,     // Stage is currently live
    CLOSED    // Stage ended
}

enum class CloseReason {
    TIMEOUT,        // Stage closed due to time limit
    HOST_LEFT,      // A host left the stage
    ALL_HOSTS_LEFT, // All hosts disconnected
    HOST_DELETED    // Host deleted the recording
}

@Entity(name = "stages")
data class StageEntity(
    @Id
    val stageId: String,
    @Enumerated(EnumType.STRING)
    val type: StageType,
    val title: String?,
    val claimId: String?,
    val topicId: String? = null,
    val eventId: String? = null,
    val challengeId: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "stageId")
    val hosts: List<StageHostEntity>,
    val createdAt: Instant,
    @Enumerated(EnumType.STRING)
    val status: StageStatus,
    val openedAt: Instant?,
    val closedAt: Instant?,
    @Enumerated(EnumType.STRING)
    val closeReason: CloseReason? = null
)
