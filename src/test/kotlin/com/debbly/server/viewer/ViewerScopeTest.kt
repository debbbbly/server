package com.debbly.server.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewerScopeTest {

    @Test
    fun `STAGE redisKey format`() {
        assertEquals("viewers:stage:abc", ViewerScope.STAGE.redisKey("abc"))
    }

    @Test
    fun `EVENT redisKey format`() {
        assertEquals("viewers:event:xyz", ViewerScope.EVENT.redisKey("xyz"))
    }
}
