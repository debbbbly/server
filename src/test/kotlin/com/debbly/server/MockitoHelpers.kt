package com.debbly.server

// Re-export mockito-kotlin's mock so existing imports keep working.
inline fun <reified T : Any> mock(): T = org.mockito.kotlin.mock()
