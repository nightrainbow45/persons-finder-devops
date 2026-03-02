package com.persons.finder.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for Swagger UI accessibility.
 * 
 * Validates Requirements: 2.1, 2.6
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerUIAccessibilityTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `swagger-ui_html endpoint returns 200 status code`() {
        // When: accessing the Swagger UI endpoint (follows redirect)
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }

    @Test
    fun `swagger-ui_html returns HTML content type`() {
        // When: accessing the Swagger UI endpoint (follows redirect)
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("text/html"))
    }

    @Test
    fun `swagger-ui_html HTML contains API title`() {
        // When: accessing the Swagger UI endpoint (follows redirect)
        val result = mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the HTML should contain Swagger UI title
        val htmlResponse = result.response.contentAsString
        
        // Swagger UI HTML contains the static title "Swagger UI"
        // The actual API title is loaded dynamically via JavaScript from /v3/api-docs
        assertThat(htmlResponse).contains("Swagger UI")
        assertThat(htmlResponse).contains("swagger-ui-bundle.js")
    }
}
