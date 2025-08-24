package com.debbly.server.followers.repository.entity

import com.debbly.server.user.UserEntity
import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant

@Embeddable
data class FollowerEntityId(
    @Column(name ="follower_user_id")
    val followerUserId: String,
    @Column(name ="following_user_id")
    val followingUserId: String
) : Serializable

@Entity
@Table(name = "followers")
data class FollowerEntity(
    @EmbeddedId
    val id: FollowerEntityId,

    val createdAt: Instant,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "follower_user_id", insertable = false, updatable = false)
    val follower: UserEntity? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "following_user_id", insertable = false, updatable = false)
    val following: UserEntity? = null
)