package com.debbly.server.followers

import com.debbly.server.followers.repository.FollowersCachedRepository
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserFollowService(
    private val followersCachedRepository: FollowersCachedRepository,
    private val userCachedRepository: UserCachedRepository
) {

    @Transactional
    fun followUser(followerId: String, followingId: String) {
        if (followerId == followingId) {
            throw IllegalArgumentException("Cannot follow yourself")
        }
        if (followersCachedRepository.isFollowing(followerId, followingId)) {
            throw IllegalStateException("Already following this user")
        }

        // Verify both users exist
        userCachedRepository.getById(followerId)
        userCachedRepository.getById(followingId)

        followersCachedRepository.followUser(followerId, followingId)
    }

    @Transactional
    fun unfollowUser(followerId: String, followingId: String) {
        if (followerId == followingId) {
            throw IllegalArgumentException("Cannot unfollow yourself")
        }
        if (!followersCachedRepository.isFollowing(followerId, followingId)) {
            throw IllegalStateException("Not currently following this user")
        }

        followersCachedRepository.unfollowUser(followerId, followingId)
    }

    fun getFollowing(userId: String): List<UserModel> {
        val followingIds = followersCachedRepository.getFollowingIdsByUserId(userId)
        return followingIds.map { userCachedRepository.getById(it) }
    }

    fun getFollowers(userId: String): List<UserModel> {
        val followerIds = followersCachedRepository.getFollowerIdsByUserId(userId)
        return followerIds.map { userCachedRepository.getById(it) }
    }

    fun getFollowingCount(userId: String): Long =
        followersCachedRepository.getFollowingCountByUserId(userId)

    fun getFollowersCount(userId: String): Long =
        followersCachedRepository.getFollowersCountByUserId(userId)

    fun isFollowing(followerId: String, followingId: String): Boolean =
        followersCachedRepository.isFollowing(followerId, followingId)
}