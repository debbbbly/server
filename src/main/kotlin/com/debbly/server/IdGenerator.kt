package com.debbly.server

object IdGenerator {
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun id(): String = (1..8)
        .map { BASE58.random() }
        .joinToString("")
}