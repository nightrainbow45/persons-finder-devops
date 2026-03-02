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
import org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for verifying that all data models and parameters include example values
 * in the OpenAPI specification.
 * 
 * Validates Requirements: 7.1, 7.2, 7.3, 7.4
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModelExamplesTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun getOpenAPISpec(): JsonNode {
        val result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)
    }

    @Test
    fun `CreatePersonRequest schema includes example in OpenAPI spec`() {
        // Given: the OpenAPI specification
        val spec = getOpenAPISpec()
        
        // When: examining the CreatePersonRequest schema
        val schemas = spec.get("components").get("schemas")
        assertThat(schemas.has("CreatePersonRequest")).isTrue()
        
        val createPersonRequest = schemas.get("CreatePersonRequest")
        
        // Then: it should have an example value
        assertThat(createPersonRequest.has("properties")).isTrue()
        val properties = createPersonRequest.get("properties")
        assertThat(properties.has("name")).isTrue()
        
        val nameProperty = properties.get("name")
        assertThat(nameProperty.has("example")).isTrue()
        assertThat(nameProperty.get("example").asText()).isEqualTo("John Doe")
    }

    @Test
    fun `UpdateLocationRequest schema includes latitude example in OpenAPI spec`() {
        // Given: the OpenAPI specification
        val spec = getOpenAPISpec()
        
        // When: examining the UpdateLocationRequest schema
        val schemas = spec.get("components").get("schemas")
        assertThat(schemas.has("UpdateLocationRequest")).isTrue()
        
        val updateLocationRequest = schemas.get("UpdateLocationRequest")
        
        // Then: it should have latitude example
        assertThat(updateLocationRequest.has("properties")).isTrue()
        val properties = updateLocationRequest.get("properties")
        assertThat(properties.has("latitude")).isTrue()
        
        val latitudeProperty = properties.get("latitude")
        assertThat(latitudeProperty.has("example")).isTrue()
        assertThat(latitudeProperty.get("example").asText()).isEqualTo("-33.8688")
    }

    @Test
    fun `UpdateLocationRequest schema includes longitude example in OpenAPI spec`() {
        // Given: the OpenAPI specification
        val spec = getOpenAPISpec()
        
        // When: examining the UpdateLocationRequest schema
        val schemas = spec.get("components").get("schemas")
        assertThat(schemas.has("UpdateLocationRequest")).isTrue()
        
        val updateLocationRequest = schemas.get("UpdateLocationRequest")
        
        // Then: it should have longitude example
        assertThat(updateLocationRequest.has("properties")).isTrue()
        val properties = updateLocationRequest.get("properties")
        assertThat(properties.has("longitude")).isTrue()
        
        val longitudeProperty = properties.get("longitude")
        assertThat(longitudeProperty.has("example")).isTrue()
        assertThat(longitudeProperty.get("example").asText()).isEqualTo("151.2093")
    }

    @Test
    fun `GET persons endpoint includes ids query parameter example`() {
        // Given: the OpenAPI specification
        val spec = getOpenAPISpec()
        
        // When: examining the GET /api/v1/persons endpoint
        val paths = spec.get("paths")
        assertThat(paths.has("/api/v1/persons")).isTrue()
        
        val personsPath = paths.get("/api/v1/persons")
        assertThat(personsPath.has("get")).isTrue()
        
        val getOperation = personsPath.get("get")
        assertThat(getOperation.has("parameters")).isTrue()
        
        // Then: the ids parameter should have an example
        val parameters = getOperation.get("parameters")
        assertThat(parameters.isArray).isTrue()
        
        val idsParameter = parameters.find { 
            it.has("name") && it.get("name").asText() == "ids" 
        }
        assertThat(idsParameter).isNotNull()
        assertThat(idsParameter!!.has("example")).isTrue()
        assertThat(idsParameter.get("example").asText()).isEqualTo("1,2,3")
    }

    @Test
    fun `GET nearby endpoint includes radius parameter with default value`() {
        // Given: the OpenAPI specification
        val spec = getOpenAPISpec()
        
        // When: examining the GET /api/v1/persons/{id}/nearby endpoint
        val paths = spec.get("paths")
        assertThat(paths.has("/api/v1/persons/{id}/nearby")).isTrue()
        
        val nearbyPath = paths.get("/api/v1/persons/{id}/nearby")
        assertThat(nearbyPath.has("get")).isTrue()
        
        val getOperation = nearbyPath.get("get")
        assertThat(getOperation.has("parameters")).isTrue()
        
        // Then: the radius parameter should have an example and default value
        val parameters = getOperation.get("parameters")
        assertThat(parameters.isArray).isTrue()
        
        val radiusParameter = parameters.find { 
            it.has("name") && it.get("name").asText() == "radius" 
        }
        assertThat(radiusParameter).isNotNull()
        assertThat(radiusParameter!!.has("example")).isTrue()
        assertThat(radiusParameter.get("example").asText()).isEqualTo("10")
        
        // Verify schema has default value
        assertThat(radiusParameter.has("schema")).isTrue()
        val schema = radiusParameter.get("schema")
        assertThat(schema.has("default")).isTrue()
        assertThat(schema.get("default").asText()).isEqualTo("10")
    }
}
