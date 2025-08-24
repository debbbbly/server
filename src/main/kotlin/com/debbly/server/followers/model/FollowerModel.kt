package com.debbly.server.followers.model

import com.debbly.server.followers.repository.entity.FollowerEntity
import com.debbly.server.followers.repository.entity.FollowerEntityId
import com.debbly.server.user.UserEntity
import java.time.Instant

data class FollowerModel(
    val followerUserId: String,
    val followingUserId: String,
    val createdAt: Instant,
    val follower: UserEntity? = null,
    val following: UserEntity? = null
)

fun FollowerEntity.toModel() = FollowerModel(
    followerUserId = id.followerUserId,
    followingUserId = id.followingUserId,
    createdAt = createdAt,
    follower = follower,
    following = following
)