package com.debbly.server.storage

import com.debbly.server.config.S3DefaultProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class S3DefaultPropertiesTest {

    @Test
    fun `buildPublicUrl uses publicEndpoint when set`() {
        val props = S3DefaultProperties(
            endpoint = "http://s3.local",
            publicEndpoint = "https://cdn.example.com",
            bucket = "my-bucket"
        )
        assertEquals("https://cdn.example.com/users/1/avatars/x.jpg", props.buildPublicUrl("users/1/avatars/x.jpg"))
    }

    @Test
    fun `buildPublicUrl falls back to endpoint plus bucket when publicEndpoint blank`() {
        val props = S3DefaultProperties(
            endpoint = "http://s3.local",
            publicEndpoint = "",
            bucket = "my-bucket"
        )
        assertEquals("http://s3.local/my-bucket/key.png", props.buildPublicUrl("key.png"))
    }

    @Test
    fun `buildPublicUrl strips leading slash on key`() {
        val props = S3DefaultProperties(
            endpoint = "http://s3.local",
            publicEndpoint = "https://cdn.example.com/",
            bucket = "b"
        )
        assertEquals("https://cdn.example.com/key", props.buildPublicUrl("/key"))
    }

    @Test
    fun `buildStageMediaPath prefixes stages`() {
        val props = S3DefaultProperties(
            endpoint = "http://s3.local",
            publicEndpoint = "https://cdn.example.com",
            bucket = "b"
        )
        assertEquals("https://cdn.example.com/stages/s1", props.buildStageMediaPath("s1"))
    }
}
