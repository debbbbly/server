package com.debbly.server.user

import com.debbly.server.moderation.ModerationApiService
import com.debbly.server.moderation.UsernameValidationResult
import com.debbly.server.user.model.UserModel
import com.debbly.server.user.repository.UserCachedRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class UsernameServiceTest {

    private val userRepo: UserCachedRepository = mock()
    private val moderation: ModerationApiService = mock()
    private val service = UsernameService(userRepo, moderation)

    @Test
    fun `rejects too short username`() {
        val result = service.validateUsername("abc")
        assertFalse(result.valid)
    }

    @Test
    fun `rejects reserved username`() {
        whenever(userRepo.findByUsername(any())).thenReturn(null)
        val result = service.validateUsername("admin123")
        assertFalse(result.valid)
    }

    @Test
    fun `rejects username containing debbly`() {
        val result = service.validateUsername("ilovedebbly")
        assertFalse(result.valid)
    }

    @Test
    fun `rejects already-taken username`() {
        whenever(userRepo.findByUsername("taken_name")).thenReturn(
            UserModel(
                userId = "u-other",
                externalUserId = "ext",
                email = "a@b.c",
                username = "taken_name",
                usernameNormalized = "taken_name",
                createdAt = Instant.parse("2025-01-01T00:00:00Z")
            )
        )
        val result = service.validateUsername("taken_name")
        assertFalse(result.valid)
    }

    @Test
    fun `delegates to moderation when name passes local checks`() {
        whenever(userRepo.findByUsername(any())).thenReturn(null)
        whenever(moderation.validateUsername(any())).thenReturn(UsernameValidationResult(true, ""))
        val result = service.validateUsername("good_name123")
        assertTrue(result.valid)
    }

    @Test
    fun `generateUsername produces valid format when repo returns null`() {
        whenever(userRepo.findByUsername(any())).thenReturn(null)
        val name = service.generateUsername()
        assertTrue(name.matches(Regex("^[a-z]+_[a-z]+_[0-9]+$")))
    }
}
