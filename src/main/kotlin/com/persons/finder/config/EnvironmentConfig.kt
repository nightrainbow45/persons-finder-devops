package com.persons.finder.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class EnvironmentConfig(
    @Value("\${openai.api-key:}")
    val openaiApiKey: String,
    // LLM_PROXY_URL: when the PII sidecar is enabled, Helm injects this as
    // "http://localhost:8081" so all LLM calls route through the sidecar proxy.
    // Defaults to the real provider so the app works without the sidecar (dev/local).
    @Value("\${llm.proxy-url:https://api.openai.com}")
    val llmProxyUrl: String = "https://api.openai.com"
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
