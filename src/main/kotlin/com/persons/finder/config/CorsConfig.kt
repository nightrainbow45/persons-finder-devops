package com.persons.finder.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS (Cross-Origin Resource Sharing) configuration for the Persons Finder API.
 * 
 * This configuration enables cross-origin requests from specified origins,
 * allowing Swagger UI and other clients to access the API from different domains.
 * 
 * Configuration is controlled via the CORS_ALLOWED_ORIGINS environment variable.
 */
@Configuration
class CorsConfig {
    
    @Value("\${cors.allowed-origins:*}")
    private lateinit var allowedOrigins: String
    
    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                // Parse allowed origins from comma-separated string
                val origins = if (allowedOrigins == "*") {
                    arrayOf("*")
                } else {
                    allowedOrigins.split(",").map { it.trim() }.toTypedArray()
                }
                
                // Configure CORS for API endpoints
                registry.addMapping("/api/**")
                    .allowedOrigins(*origins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                    .allowCredentials(origins[0] != "*")
                    .maxAge(3600)
                
                // Configure CORS for Swagger/OpenAPI endpoints
                registry.addMapping("/v3/api-docs/**")
                    .allowedOrigins(*origins)
                    .allowedMethods("GET", "OPTIONS")
                    .allowedHeaders("Content-Type")
                    .maxAge(3600)
            }
        }
    }
}
