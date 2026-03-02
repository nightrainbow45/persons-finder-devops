package com.persons.finder.config

import com.persons.finder.pii.PiiProxyService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for the LLM integration layer.
 *
 * Registers [PiiProxyService] as a singleton Bean pre-configured with the
 * resolved LLM base URL. When the PII sidecar is enabled in the Helm chart,
 * [EnvironmentConfig.llmProxyUrl] is "http://localhost:8081" (the sidecar port),
 * so all LLM calls are routed through the sidecar for Layer 2 PII protection.
 *
 * Without the sidecar, the URL defaults to "https://api.openai.com" and
 * Layer 1 (in-process PiiProxyService) handles redaction directly.
 *
 * Inject [PiiProxyService] wherever you need to call the LLM:
 *   class MyService(private val piiProxy: PiiProxyService) {
 *       fun ask(prompt: String) = piiProxy.callLlm("/v1/chat/completions", body, headers)
 *   }
 */
@Configuration
class LlmConfig(private val environmentConfig: EnvironmentConfig) {

    @Bean
    fun piiProxyService(): PiiProxyService =
        PiiProxyService(llmBaseUrl = environmentConfig.llmProxyUrl)
}
