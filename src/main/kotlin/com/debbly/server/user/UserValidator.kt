package com.debbly.server.user

import com.debbly.server.user.model.UserModel
import java.time.LocalDate
import java.time.Period

object UserValidator {
    val invalidCharsRegex = Regex("[^a-zA-Z0-9_]")

    fun isValidBirthdate(birthdate: LocalDate) =
        Period.between(birthdate, LocalDate.now()).years >= 14


    fun isUserComplete(user: UserModel) = user.username != null

}