package com.debbly.server.claim

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchOptionsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `test getOptions endpoint`() {
        mockMvc.perform(get("/claims/top"))
            .andExpect(status().isOk)
    }
}
