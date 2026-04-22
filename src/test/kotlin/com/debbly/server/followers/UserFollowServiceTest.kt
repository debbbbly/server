package com.debbly.server.followers

import com.debbly.server.followers.repository.FollowersCachedRepository
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class UserFollowServiceTest {

    private val followersRepo: FollowersCachedRepository = mock()
    private val userRepo: UserCachedRepository = mock()
    private val service = UserFollowService(followersRepo, userRepo)

    private fun user(id: String) = UserModel(
        userId = id,
        externalUserId = "ext-$id",
        email = "$id@x.y",
        username = id,
        usernameNormalized = id,
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    @Test
    fun `followUser throws when following self`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.followUser("u1", "u1")
        }
    }

    @Test
    fun `followUser throws when already following`() {
        whenever(followersRepo.isFollowing("u1", "u2")).thenReturn(true)
        assertThrows(IllegalStateException::class.java) {
            service.followUser("u1", "u2")
        }
    }

    @Test
    fun `followUser saves on happy path`() {
        whenever(followersRepo.isFollowing("u1", "u2")).thenReturn(false)
        whenever(userRepo.getById("u1")).thenReturn(user("u1"))
        whenever(userRepo.getById("u2")).thenReturn(user("u2"))

        service.followUser("u1", "u2")

        verify(followersRepo).followUser("u1", "u2")
    }

    @Test
    fun `unfollowUser throws when not following`() {
        whenever(followersRepo.isFollowing("u1", "u2")).thenReturn(false)
        assertThrows(IllegalStateException::class.java) {
            service.unfollowUser("u1", "u2")
        }
    }

    @Test
    fun `getFollowing maps ids to users`() {
        whenever(followersRepo.getFollowingIdsByUserId("u1")).thenReturn(listOf("u2"))
        whenever(userRepo.getById("u2")).thenReturn(user("u2"))

        val result = service.getFollowing("u1")
        assertEquals(1, result.size)
        assertEquals("u2", result[0].userId)
    }

    @Test
    fun `isFollowing delegates to repo`() {
        whenever(followersRepo.isFollowing("u1", "u2")).thenReturn(true)
        assertTrue(service.isFollowing("u1", "u2"))
    }
}
