package com.debbly.server.followers.repository

import com.debbly.server.followers.repository.entity.FollowerEntity
import com.debbly.server.followers.repository.entity.FollowerEntityId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FollowersJpaRepository : JpaRepository<FollowerEntity, FollowerEntityId> {
    
    @Query("SELECT uf.id.followingUserId FROM FollowerEntity uf WHERE uf.id.followerUserId = :userId")
    fun findFollowingUserIdsByUserId(userId: String): List<String>
    
    @Query("SELECT uf.id.followerUserId FROM FollowerEntity uf WHERE uf.id.followingUserId = :userId")
    fun findFollowerUserIdsByUserId(userId: String): List<String>
    
    @Query("SELECT COUNT(uf) FROM FollowerEntity uf WHERE uf.id.followerUserId = :userId")
    fun countFollowingByUserId(userId: String): Long
    
    @Query("SELECT COUNT(uf) FROM FollowerEntity uf WHERE uf.id.followingUserId = :userId")
    fun countFollowersByUserId(userId: String): Long
    
    fun existsByIdFollowerUserIdAndIdFollowingUserId(followerUserId: String, followingUserId: String): Boolean
}