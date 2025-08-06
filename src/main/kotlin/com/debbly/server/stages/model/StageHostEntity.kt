package com.debbly.server.stages.model

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import java.io.Serializable

@Entity(name = "stages")
data class StageHostEntity(
    @EmbeddedId
    val id: StageHostId
)

@Embeddable
data class StageHostId(
    val stageId: String,
    val userId: String
) : Serializable