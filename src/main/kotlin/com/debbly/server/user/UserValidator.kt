package com.debbly.server.user

import com.debbly.server.user.model.UserModel
import java.time.LocalDate
import java.time.Period

object UserValidator {
    val usernameRegex = Regex("^[a-zA-Z0-9_]{6,18}$")
    val invalidCharsRegex = Regex("[^a-zA-Z0-9_]")

    fun isValidBirthdate(birthdate: LocalDate) =
        Period.between(birthdate, LocalDate.now()).years >= 14

    fun isValidUsername(username: String): Boolean {
        return username.trim().matches(usernameRegex)
    }

    fun isUserComplete(user: UserModel) = user.username != null

}