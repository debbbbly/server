package com.debbly.server.user

import com.debbly.server.IdService
import com.debbly.server.auth.service.AuthService
import com.debbly.server.moderation.BioValidationResult
import com.debbly.server.moderation.ModerationApiService
import com.debbly.server.moderation.UsernameValidationResult
import com.debbly.server.storage.S3Service
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.SocialUsernameCachedRepository
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.CacheManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserServiceTest {

    private val userRepo: UserCachedRepository = mock()
    private val socialRepo: SocialUsernameCachedRepository = mock()
    private val idService: IdService = mock()
    private val moderation: ModerationApiService = mock()
    private val s3: S3Service = mock()
    private val cacheManager: CacheManager = mock()
    private val auth: AuthService = mock()
    private val usernameService: UsernameService = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

    private val service = UserService(
        userRepo, socialRepo, idService, moderation, s3, cacheManager, auth, usernameService, clock
    )

    private fun sampleUser() = UserModel(
        userId = "u1",
        externalUserId = "ext1",
        email = "a@b.c",
        username = "alice",
        usernameNormalized = "alice",
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    @Test
    fun `createUser returns existing user when found`() {
        val existing = sampleUser()
        whenever(userRepo.findByExternalUserId("ext1")).thenReturn(existing)

        val result = service.createUser("ext1", "a@b.c")

        assertEquals(existing, result)
    }

    @Test
    fun `createUser creates new user when not found`() {
        whenever(userRepo.findByExternalUserId("ext1")).thenReturn(null)
        whenever(usernameService.generateUsername()).thenReturn("happy_apple_1")
        whenever(idService.getId()).thenReturn("u-new")
        whenever(userRepo.save(any())).thenAnswer { it.arguments[0] as UserModel }

        val result = service.createUser("ext1", "a@b.c")

        assertEquals("u-new", result.userId)
        assertEquals("happy_apple_1", result.username)
        assertEquals("happy_apple_1", result.usernameNormalized)
    }

    @Test
    fun `updateUsername fails when validation fails`() {
        whenever(usernameService.validateUsername("bad", "u1"))
            .thenReturn(UsernameValidationResult(false, "bad name"))

        val result = service.updateUsername(sampleUser(), "bad")

        assertFalse(result.success)
        assertEquals("bad name", result.message)
    }

    @Test
    fun `updateUsername succeeds on valid username`() {
        val user = sampleUser()
        whenever(usernameService.validateUsername("newname", "u1"))
            .thenReturn(UsernameValidationResult(true, ""))
        whenever(userRepo.save(any())).thenAnswer { it.arguments[0] as UserModel }

        val result = service.updateUsername(user, "newname")

        assertTrue(result.success)
        assertEquals("newname", user.username)
        verify(userRepo).save(user)
    }

    @Test
    fun `updateAvatar rejects key not owned by user`() {
        val user = sampleUser()
        whenever(s3.isAvatarKeyOwnedByUser("u1", "bad/key")).thenReturn(false)

        val result = service.updateAvatar(user, "bad/key")

        assertFalse(result.success)
    }

    @Test
    fun `updateAvatar sets new avatar url on valid key`() {
        val user = sampleUser()
        whenever(s3.isAvatarKeyOwnedByUser("u1", "users/u1/avatars/x.jpg")).thenReturn(true)
        whenever(s3.buildUsersPublicUrl("users/u1/avatars/x.jpg")).thenReturn("https://cdn/users/u1/avatars/x.jpg")
        whenever(userRepo.save(any())).thenAnswer { it.arguments[0] as UserModel }

        val result = service.updateAvatar(user, "users/u1/avatars/x.jpg")

        assertTrue(result.success)
        assertEquals("https://cdn/users/u1/avatars/x.jpg", result.avatarUrl)
        assertEquals("https://cdn/users/u1/avatars/x.jpg", user.avatarUrl)
    }

    @Test
    fun `updateBio rejects overly long bio`() {
        val result = service.updateBio(sampleUser(), "x".repeat(1025))
        assertFalse(result.success)
    }

    @Test
    fun `updateBio rejects moderation-flagged bio`() {
        whenever(moderation.validateBio(any())).thenReturn(BioValidationResult(false, "bad"))
        val result = service.updateBio(sampleUser(), "hello")
        assertFalse(result.success)
    }

    @Test
    fun `updateBio succeeds on valid bio`() {
        val user = sampleUser()
        whenever(moderation.validateBio("hello")).thenReturn(BioValidationResult(true, ""))
        whenever(userRepo.save(any())).thenAnswer { it.arguments[0] as UserModel }

        val result = service.updateBio(user, "hello")

        assertTrue(result.success)
        assertEquals("hello", user.bio)
    }

    @Test
    fun `updateSocialUsernames rejects blank username`() {
        val result = service.updateSocialUsernames("u1", mapOf(SocialType.TWITTER to ""))
        assertFalse(result.success)
    }

    @Test
    fun `updateSocialUsernames rejects overly long username`() {
        val result = service.updateSocialUsernames("u1", mapOf(SocialType.TWITTER to "x".repeat(256)))
        assertFalse(result.success)
    }

    @Test
    fun `updateSocialUsernames saves valid entries`() {
        val result = service.updateSocialUsernames("u1", mapOf(SocialType.TWITTER to "alice"))
        assertTrue(result.success)
        verify(socialRepo).saveAll(eq("u1"), any())
    }
}
