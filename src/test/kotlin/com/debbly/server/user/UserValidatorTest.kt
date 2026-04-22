package com.debbly.server.user

import com.debbly.server.user.model.UserModel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class UserValidatorTest {

    @Test
    fun `isValidBirthdate accepts age 14 and above`() {
        val birthdate = LocalDate.now().minusYears(14)
        assertTrue(UserValidator.isValidBirthdate(birthdate))
    }

    @Test
    fun `isValidBirthdate rejects age below 14`() {
        val birthdate = LocalDate.now().minusYears(13)
        assertFalse(UserValidator.isValidBirthdate(birthdate))
    }

    @Test
    fun `isUserComplete returns true when username present`() {
        val user = userModel(username = "alice")
        assertTrue(UserValidator.isUserComplete(user))
    }

    private fun userModel(username: String) = UserModel(
        userId = "u1",
        externalUserId = "ext1",
        email = "a@b.c",
        username = username,
        usernameNormalized = username.lowercase(),
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )
}
