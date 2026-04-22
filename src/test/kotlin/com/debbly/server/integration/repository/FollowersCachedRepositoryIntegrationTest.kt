package com.debbly.server.integration.repository

import com.debbly.server.followers.repository.FollowersCachedRepository
import com.debbly.server.integration.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class FollowersCachedRepositoryIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var repo: FollowersCachedRepository

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
        insertUser("alice")
        insertUser("bob")
    }

    @Test
    fun `follow then unfollow round-trip`() {
        repo.followUser("alice", "bob")

        assertTrue(repo.isFollowing("alice", "bob"))
        assertEquals(listOf("bob"), repo.getFollowingIdsByUserId("alice"))
        assertEquals(listOf("alice"), repo.getFollowerIdsByUserId("bob"))
        assertEquals(1L, repo.getFollowingCountByUserId("alice"))
        assertEquals(1L, repo.getFollowersCountByUserId("bob"))

        repo.unfollowUser("alice", "bob")

        assertFalse(repo.isFollowing("alice", "bob"))
        assertEquals(0L, repo.getFollowingCountByUserId("alice"))
        assertEquals(0L, repo.getFollowersCountByUserId("bob"))
    }
}
