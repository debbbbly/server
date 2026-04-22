package com.debbly.server.integration.service

import com.debbly.server.integration.AbstractIntegrationTest
import com.debbly.server.user.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        flushRedis()
        clearAllCaches()
        wipeDatabase()
    }

    @Test
    fun `createUser persists new user on first call and returns the same user on second`() {
        val first = userService.createUser("ext-123", "new@test.dev")
        val second = userService.createUser("ext-123", "new@test.dev")

        assertNotNull(first.userId)
        assertEquals(first.userId, second.userId)
        assertEquals("ext-123", first.externalUserId)
    }
}
