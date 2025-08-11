package com.debbly.server.stage.model

import com.debbly.server.claim.ClaimEntity
import jakarta.persistence.*
import java.time.Instant

@NamedEntityGraph(
    name = "Stage.withHosts",
    attributeNodes = [NamedAttributeNode("hosts")]
)
@Entity(name = "stages")
data class StageEntity(
    @Id
    val stageId: String,
    @Enumerated(EnumType.STRING)
    val type: StageType,
    val topic: String?,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "claimId")
    val claim: ClaimEntity?,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "stageId")
    val hosts: Set<StageHostEntity>,
    val createdAt: Instant,
    val closedAt: Instant? = null
)

enum class StageType {
    SOLO,
    ONE_ON_ONE
}