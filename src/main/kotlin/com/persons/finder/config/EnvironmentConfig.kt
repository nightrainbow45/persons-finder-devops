package com.persons.finder.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EnvironmentConfig(
    @Value("\${openai.api-key:}")
    val openaiApiKey: String
) {
    private val logger = LoggerFactory.getLogger(EnvironmentConfig::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun validateEnvironment() {
        if (openaiApiKey.isBlank()) {
            logger.warn(
                "WARNING: Environment variable OPENAI_API_KEY is not set. " +
                "LLM integration features will not be available. " +
                "Set the OPENAI_API_KEY environment variable to enable LLM features."
            )
        } else {
            logger.info("OPENAI_API_KEY is configured. LLM integration features are available.")
        }
    }

    fun isApiKeyConfigured(): Boolean = openaiApiKey.isNotBlank()
}
