package com.debbly.server

import org.springframework.stereotype.Service

@Service
class IdService () {

    companion object {
        private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    }

    fun id(): String = (1..8)
        .map { BASE58.random() }
        .joinToString("")
}