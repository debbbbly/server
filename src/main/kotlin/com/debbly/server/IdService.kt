package com.debbly.server

import org.springframework.stereotype.Service

@Service
class IdService () {

    companion object {
        private const val ID_BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private const val ID_LENGTH = 8
    }

    fun getId(): String = (1..ID_LENGTH)
        .map { ID_BASE58.random() }
        .joinToString("")
}