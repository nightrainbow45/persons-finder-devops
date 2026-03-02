package com.persons.finder.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenAPIConfig {
    
    @Value("\${api.info.title}")
    private lateinit var title: String
    
    @Value("\${api.info.version}")
    private lateinit var version: String
    
    @Value("\${api.info.description}")
    private lateinit var description: String
    
    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title(title)
                .version(version)
                .description(description)
                .contact(Contact()
                    .name("DevOps Team")
                    .email("devops@example.com"))
                .license(License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(listOf(
                Server()
                    .url("/")
                    .description("Current server")
            ))
    }
}
