package com.debbly.server.user

import com.debbly.server.user.repository.UserCachedRepository
import com.debbly.server.user.repository.UserJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    private lateinit var userCachedRepository: UserCachedRepository

    @MockBean
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun setUp() {
        cacheManager.getCache("users")?.clear()
        cacheManager.getCache("usersByExternalId")?.clear()
        cacheManager.getCache("usersByUsername")?.clear()
    }

    @Test
    fun `test findById caching`() {
        val userId = "testId"
        val user = UserEntity(userId = userId, externalUserId = "extId", email = "test@test.com", username = "testuser")

        `when`(userJpaRepository.findById(userId)).thenReturn(Optional.of(user))

        // First call - should hit the repository
        var result = userCachedRepository.findById(userId)
        assertEquals(user, result)

        // Second call - should be cached
        result = userCachedRepository.findById(userId)
        assertEquals(user, result)

        verify(userJpaRepository, times(1)).findById(userId)
    }

    @Test
    fun `test findByExternalUserId caching`() {
        val externalId = "extId"
        val user =
            UserEntity(userId = "testId", externalUserId = externalId, email = "test@test.com", username = "testuser")

        `when`(userJpaRepository.findByExternalUserId(externalId)).thenReturn(Optional.of(user))

        // First call - should hit the repository
        var result = userCachedRepository.findByExternalUserId(externalId)
        assertEquals(user, result)

        // Second call - should be cached
        result = userCachedRepository.findByExternalUserId(externalId)
        assertEquals(user, result)

        verify(userJpaRepository, times(1)).findByExternalUserId(externalId)
    }

    @Test
    fun `test findByUsername caching`() {
        val username = "testuser"
        val user = UserEntity(userId = "testId", externalUserId = "extId", email = "test@test.com", username = username)

        `when`(userJpaRepository.findByUsername(username)).thenReturn(Optional.of(user))

        // First call - should hit the repository
        var result = userCachedRepository.findByUsername(username)
        assertEquals(user, result)

        // Second call - should be cached
        result = userCachedRepository.findByUsername(username)
        assertEquals(user, result)

        verify(userJpaRepository, times(1)).findByUsername(username)
    }
}