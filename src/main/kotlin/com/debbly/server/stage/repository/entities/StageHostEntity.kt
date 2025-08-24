package com.debbly.server.stage.repository.entities

import com.debbly.server.claim.model.ClaimSide
import jakarta.persistence.*
import java.io.Serializable

@Entity(name = "stage_hosts")
data class StageHostEntity(
    @EmbeddedId
    val id: StageHostId,
    @Enumerated(EnumType.STRING)
    val side: ClaimSide?
)

@Embeddable
data class StageHostId(
    val stageId: String,
    val userId: String
) : Serializable