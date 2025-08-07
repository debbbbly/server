package com.debbly.server.stage.model

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.OneToMany
import java.time.Instant

@NamedEntityGraph(
    name = "Stage.withHosts",
    attributeNodes = [NamedAttributeNode("hosts")]
)
@Entity(name = "stages")
data class StageEntity(
    @Id
    val stageId: String,
    val type: StageType,
    val title: String?,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "stageId")
    val hosts: Set<StageHostEntity>,
    val createdAt: Instant,
    val closedAt: Instant? = null
)

enum class StageType {
    SOLO,
    ONE_ON_ONE
}