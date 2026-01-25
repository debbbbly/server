package com.debbly.server.stage.repository.entities

import com.debbly.server.stage.model.StageType
import jakarta.persistence.*
import java.time.Instant

enum class StageStatus {
    PENDING,  // Stage created but not opened yet
    OPEN,     // Stage is currently live
    CLOSED,   // Stage ended without recording
    RECORDED  // Stage ended with recording available
}

@Entity(name = "stages")
data class StageEntity(
    @Id
    val stageId: String,
    @Enumerated(EnumType.STRING)
    val type: StageType,
    val title: String?,
    val claimId: String?,
    @Column(name = "topic_id")
    val topicId: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "stageId")
    val hosts: List<StageHostEntity>,
    val createdAt: Instant,
    @Enumerated(EnumType.STRING)
    val status: StageStatus,
    val openedAt: Instant?,
    val closedAt: Instant?,
    val hlsUrl: String? = null,
    val recorded: Boolean = false
)
