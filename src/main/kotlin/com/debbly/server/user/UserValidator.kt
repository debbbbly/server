package com.debbly.server.user

import com.debbly.server.user.model.UserModel
import java.time.LocalDate
import java.time.Period

object UserValidator {
    fun isValidBirthdate(birthdate: LocalDate) =
        Period.between(birthdate, LocalDate.now()).years >= 18

    fun isValidUsername(username: String) =
        username.trim().matches(Regex("^[a-zA-Z0-9_]{5,30}$"))

    fun isUserComplete(user: UserModel) = user.birthdate != null || user.username != null

}