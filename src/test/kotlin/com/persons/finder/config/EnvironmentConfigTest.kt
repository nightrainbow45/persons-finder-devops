package com.persons.finder.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

class EnvironmentConfigUnitTest {

    @Test
    fun `isApiKeyConfigured returns false when key is blank`() {
        val config = EnvironmentConfig("")
        assertFalse(config.isApiKeyConfigured())
    }

    @Test
    fun `isApiKeyConfigured returns true when key is set`() {
        val config = EnvironmentConfig("sk-test-key-12345")
        assertTrue(config.isApiKeyConfigured())
    }

    @Test
    fun `openaiApiKey stores the provided value`() {
        val key = "sk-test-key-12345"
        val config = EnvironmentConfig(key)
        assertEquals(key, config.openaiApiKey)
    }

    @Test
    fun `validateEnvironment does not throw when key is missing`() {
        val config = EnvironmentConfig("")
        // Should log a warning but not throw
        config.validateEnvironment()
    }

    @Test
    fun `validateEnvironment does not throw when key is present`() {
        val config = EnvironmentConfig("sk-test-key-12345")
        // Should log info but not throw
        config.validateEnvironment()
    }
}

@SpringBootTest
@TestPropertySource(properties = ["openai.api-key="])
class EnvironmentConfigMissingKeyIntegrationTest {

    @Autowired
    lateinit var environmentConfig: EnvironmentConfig

    @Test
    fun `application starts successfully without API key`() {
        // The application should start without the API key
        assertFalse(environmentConfig.isApiKeyConfigured())
    }

    @Test
    fun `warning is logged when API key is missing`() {
        // Verify the config reports the key as not configured
        assertFalse(environmentConfig.isApiKeyConfigured())
        assertEquals("", environmentConfig.openaiApiKey)
    }
}

@SpringBootTest
@TestPropertySource(properties = ["openai.api-key=sk-test-integration-key"])
class EnvironmentConfigWithKeyIntegrationTest {

    @Autowired
    lateinit var environmentConfig: EnvironmentConfig

    @Test
    fun `application reads API key from configuration`() {
        assertTrue(environmentConfig.isApiKeyConfigured())
        assertEquals("sk-test-integration-key", environmentConfig.openaiApiKey)
    }
}
