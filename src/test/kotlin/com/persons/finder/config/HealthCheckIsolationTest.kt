package com.persons.finder.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests to verify that the health check endpoint remains functional
 * when Swagger is disabled.
 * 
 * Validates Requirements: 9.1, 9.2, 9.3, 9.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["SWAGGER_ENABLED=false"])
class HealthCheckIsolationWithSwaggerDisabledTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns 200 when Swagger is disabled`() {
        // When: accessing the health endpoint with Swagger disabled
        mockMvc.perform(get("/actuator/health"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: it should have a valid status field
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `health endpoint returns correct response content when Swagger is disabled`() {
        // When: accessing the health endpoint with Swagger disabled
        mockMvc.perform(get("/actuator/health"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: it should have the expected structure
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.components").exists())
            // And: status should be UP
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `liveness probe works when Swagger is disabled`() {
        // When: accessing the liveness probe with Swagger disabled
        mockMvc.perform(get("/actuator/health/liveness"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: status should be UP
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `readiness probe works when Swagger is disabled`() {
        // When: accessing the readiness probe with Swagger disabled
        mockMvc.perform(get("/actuator/health/readiness"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: status should be UP
            .andExpect(jsonPath("$.status").value("UP"))
    }
}

/**
 * Tests to verify that the health check endpoint remains functional
 * when Swagger is enabled.
 * 
 * Validates Requirements: 9.1, 9.2, 9.3, 9.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["SWAGGER_ENABLED=true"])
class HealthCheckIsolationWithSwaggerEnabledTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns 200 when Swagger is enabled`() {
        // When: accessing the health endpoint with Swagger enabled
        mockMvc.perform(get("/actuator/health"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: it should have a valid status field
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `health endpoint returns correct response content when Swagger is enabled`() {
        // When: accessing the health endpoint with Swagger enabled
        mockMvc.perform(get("/actuator/health"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: it should have the expected structure
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.components").exists())
            // And: status should be UP
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `liveness probe works when Swagger is enabled`() {
        // When: accessing the liveness probe with Swagger enabled
        mockMvc.perform(get("/actuator/health/liveness"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: status should be UP
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `readiness probe works when Swagger is enabled`() {
        // When: accessing the readiness probe with Swagger enabled
        mockMvc.perform(get("/actuator/health/readiness"))
            // Then: it should return 200 OK
            .andExpect(status().isOk)
            // And: status should be UP
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `health check response time is not significantly impacted by Swagger`() {
        // When: accessing the health endpoint multiple times
        val startTime = System.currentTimeMillis()
        
        repeat(10) {
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk)
        }
        
        val endTime = System.currentTimeMillis()
        val averageTime = (endTime - startTime) / 10.0
        
        // Then: average response time should be reasonable (< 100ms per request)
        // This ensures Swagger doesn't significantly impact health check performance
        assert(averageTime < 100) {
            "Health check average response time ($averageTime ms) exceeds threshold (100ms)"
        }
    }
}
