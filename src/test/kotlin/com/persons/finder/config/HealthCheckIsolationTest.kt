package com.persons.finder.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for verifying that health check endpoint remains functional
 * regardless of Swagger configuration state.
 * 
 * Validates Requirements: 9.1, 9.2, 9.3, 9.4
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthCheckIsolationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `health check endpoint returns 200 when Swagger is enabled`() {
        // Given: Swagger is enabled (default configuration)
        
        // When: accessing the health check endpoint
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `health check endpoint returns valid JSON response`() {
        // When: accessing the health check endpoint
        val result = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the response should be valid JSON
        val jsonResponse = result.response.contentAsString
        val healthNode = objectMapper.readTree(jsonResponse)
        
        assertThat(healthNode.has("status")).isTrue()
        assertThat(healthNode.get("status").asText()).isEqualTo("UP")
    }

    @Test
    fun `health check endpoint contains status field`() {
        // When: accessing the health check endpoint
        val result = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the response should contain a status field
        val jsonResponse = result.response.contentAsString
        val healthNode = objectMapper.readTree(jsonResponse)
        
        assertThat(healthNode.has("status")).isTrue()
        val status = healthNode.get("status").asText()
        assertThat(status).isNotBlank()
    }

    @Test
    fun `health check endpoint does not contain Swagger-specific information`() {
        // When: accessing the health check endpoint
        val result = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the response should not contain Swagger-specific fields
        val jsonResponse = result.response.contentAsString
        
        assertThat(jsonResponse.lowercase()).doesNotContain("swagger")
        assertThat(jsonResponse.lowercase()).doesNotContain("openapi")
    }

    @Test
    fun `health check endpoint is independent of Swagger UI accessibility`() {
        // Given: we can access Swagger UI
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
        
        // When: accessing the health check endpoint
        val result = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: health check should still work independently
        val jsonResponse = result.response.contentAsString
        val healthNode = objectMapper.readTree(jsonResponse)
        
        assertThat(healthNode.get("status").asText()).isEqualTo("UP")
    }

    @Test
    fun `health check endpoint is independent of OpenAPI spec accessibility`() {
        // Given: we can access OpenAPI spec
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
        
        // When: accessing the health check endpoint
        val result = mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: health check should still work independently
        val jsonResponse = result.response.contentAsString
        val healthNode = objectMapper.readTree(jsonResponse)
        
        assertThat(healthNode.get("status").asText()).isEqualTo("UP")
    }

    @Test
    fun `health check response time is not affected by Swagger`() {
        // When: accessing the health check endpoint multiple times
        val responseTimes = mutableListOf<Long>()
        
        repeat(5) {
            val startTime = System.currentTimeMillis()
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk)
            val endTime = System.currentTimeMillis()
            responseTimes.add(endTime - startTime)
        }

        // Then: response times should be consistently fast (< 1000ms)
        responseTimes.forEach { responseTime ->
            assertThat(responseTime).isLessThan(1000)
        }
    }
}
