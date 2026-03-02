package com.persons.finder.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `OPTIONS request to API endpoint should return CORS headers`() {
        val origin = "https://example.com"
        
        mockMvc.perform(
            options("/api/v1/persons")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
    }

    @Test
    fun `CORS should return Access-Control-Allow-Origin header`() {
        val origin = "https://example.com"
        
        val result = mockMvc.perform(
            options("/api/v1/persons")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andReturn()
        
        val allowOriginHeader = result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        assert(allowOriginHeader != null)
    }

    @Test
    fun `CORS should allow required HTTP methods`() {
        val origin = "https://example.com"
        
        val result = mockMvc.perform(
            options("/api/v1/persons")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
            .andReturn()
        
        val allowMethodsHeader = result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
        assert(allowMethodsHeader != null)
        
        val requiredMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        requiredMethods.forEach { method ->
            assert(allowMethodsHeader!!.contains(method))
        }
    }

    @Test
    fun `CORS should allow required request headers`() {
        val origin = "https://example.com"
        
        val result = mockMvc.perform(
            options("/api/v1/persons")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization, X-Requested-With")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
            .andReturn()
        
        val allowHeadersHeader = result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
        assert(allowHeadersHeader != null)
        
        val requiredHeaders = listOf("Content-Type", "Authorization", "X-Requested-With")
        requiredHeaders.forEach { header ->
            assert(allowHeadersHeader!!.contains(header, ignoreCase = true))
        }
    }

    @Test
    fun `CORS should be configured for OpenAPI documentation endpoint`() {
        val origin = "https://example.com"
        
        mockMvc.perform(
            options("/v3/api-docs")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
    }

    @Test
    fun `CORS should work for all API endpoints`() {
        val endpoints = listOf(
            "/api/v1/persons",
            "/api/v1/persons/1/location",
            "/api/v1/persons/1/nearby"
        )
        val origin = "https://example.com"
        
        endpoints.forEach { endpoint ->
            mockMvc.perform(
                options(endpoint)
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            )
                .andExpect(status().isOk)
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        }
    }

    @Test
    fun `CORS should include Access-Control-Max-Age header`() {
        val origin = "https://example.com"
        
        val result = mockMvc.perform(
            options("/api/v1/persons")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        )
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
            .andReturn()
        
        val maxAgeHeader = result.response.getHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE)
        assert(maxAgeHeader == "3600")
    }
}
