package com.debbly.server.followers.repository

import com.debbly.server.followers.model.FollowerModel
import com.debbly.server.followers.model.toModel
import com.debbly.server.followers.repository.entity.FollowerEntity
import com.debbly.server.followers.repository.entity.FollowerEntityId
import com.debbly.server.user.repository.UserJpaRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class FollowersCachedRepository(
    private val followersJpaRepository: FollowersJpaRepository,
    private val userJpaRepository: UserJpaRepository
) {

    @Cacheable(value = ["userFollowing"], key = "#userId", unless = "#result.isEmpty()")
    fun getFollowingIdsByUserId(userId: String): List<String> = 
        followersJpaRepository.findFollowingUserIdsByUserId(userId)

    @Cacheable(value = ["userFollowers"], key = "#userId", unless = "#result.isEmpty()")
    fun getFollowerIdsByUserId(userId: String): List<String> = 
        followersJpaRepository.findFollowerUserIdsByUserId(userId)

    @Cacheable(value = ["userFollowingCount"], key = "#userId")
    fun getFollowingCountByUserId(userId: String): Long = 
        followersJpaRepository.countFollowingByUserId(userId)

    @Cacheable(value = ["userFollowersCount"], key = "#userId")
    fun getFollowersCountByUserId(userId: String): Long = 
        followersJpaRepository.countFollowersByUserId(userId)

    fun isFollowing(followerId: String, followingId: String): Boolean = 
        followersJpaRepository.existsByIdFollowerUserIdAndIdFollowingUserId(followerId, followingId)

    @Caching(
        evict = [
            CacheEvict(value = ["userFollowing", "userFollowers", "userFollowingCount", "userFollowersCount"], key = "#followerUserId"),
            CacheEvict(value = ["userFollowing", "userFollowers", "userFollowingCount", "userFollowersCount"], key = "#followingUserId")
        ]
    )
    fun followUser(followerUserId: String, followingUserId: String): FollowerModel {
        val followEntity = FollowerEntity(
            id = FollowerEntityId(followerUserId, followingUserId,),
            Instant.now()
        )
        return followersJpaRepository.save(followEntity).toModel()
    }

    @Caching(
        evict = [
            CacheEvict(value = ["userFollowing", "userFollowers", "userFollowingCount", "userFollowersCount"], key = "#followerUserId"),
            CacheEvict(value = ["userFollowing", "userFollowers", "userFollowingCount", "userFollowersCount"], key = "#followingUserId")
        ]
    )
    fun unfollowUser(followerUserId: String, followingUserId: String) {
        val followId = FollowerEntityId(followerUserId, followingUserId)
        followersJpaRepository.deleteById(followId)
    }
}