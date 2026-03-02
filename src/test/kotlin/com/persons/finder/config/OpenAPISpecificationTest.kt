package com.persons.finder.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
 * Unit tests for OpenAPI specification generation and accessibility.
 * 
 * Validates Requirements: 1.5, 2.6
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenAPISpecificationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `v3 api-docs endpoint returns 200 status code`() {
        // When: accessing the OpenAPI specification endpoint
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
    }

    @Test
    fun `v3 api-docs returns valid JSON with OpenAPI 3_0_x version`() {
        // When: accessing the OpenAPI specification endpoint
        val result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the response should contain valid JSON with OpenAPI version 3.0.x
        val jsonResponse = result.response.contentAsString
        val jsonNode: JsonNode = objectMapper.readTree(jsonResponse)
        
        assertThat(jsonNode.has("openapi")).isTrue()
        val openapiVersion = jsonNode.get("openapi").asText()
        assertThat(openapiVersion).matches("^3\\.0\\.\\d+$")
    }

    @Test
    fun `v3 api-docs contains all expected API paths`() {
        // When: accessing the OpenAPI specification endpoint
        val result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the specification should contain all expected paths
        val jsonResponse = result.response.contentAsString
        val jsonNode: JsonNode = objectMapper.readTree(jsonResponse)
        
        assertThat(jsonNode.has("paths")).isTrue()
        val paths = jsonNode.get("paths")
        
        // Verify all expected endpoints are present
        assertThat(paths.has("/api/v1/persons")).isTrue()
        assertThat(paths.has("/api/v1/persons/{id}/location")).isTrue()
        assertThat(paths.has("/api/v1/persons/{id}/nearby")).isTrue()
    }

    @Test
    fun `v3 api-docs contains correct API information`() {
        // When: accessing the OpenAPI specification endpoint
        val result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the specification should contain correct API information
        val jsonResponse = result.response.contentAsString
        val jsonNode: JsonNode = objectMapper.readTree(jsonResponse)
        
        assertThat(jsonNode.has("info")).isTrue()
        val info = jsonNode.get("info")
        
        // Verify API title, version, and description are present
        assertThat(info.has("title")).isTrue()
        assertThat(info.get("title").asText()).isNotBlank()
        
        assertThat(info.has("version")).isTrue()
        assertThat(info.get("version").asText()).isNotBlank()
        
        assertThat(info.has("description")).isTrue()
        assertThat(info.get("description").asText()).isNotBlank()
    }

    @Test
    fun `v3 api-docs API information matches configured values`() {
        // When: accessing the OpenAPI specification endpoint
        val result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()

        // Then: the API information should match the configured values
        val jsonResponse = result.response.contentAsString
        val jsonNode: JsonNode = objectMapper.readTree(jsonResponse)
        
        val info = jsonNode.get("info")
        
        // Verify default values (or environment-configured values)
        val title = info.get("title").asText()
        val version = info.get("version").asText()
        val description = info.get("description").asText()
        
        // These should match the defaults in application.properties
        // or be overridden by environment variables
        assertThat(title).isEqualTo("Persons Finder API")
        assertThat(version).isEqualTo("1.0.0")
        assertThat(description).isEqualTo("REST API for managing persons and their locations")
    }
}
