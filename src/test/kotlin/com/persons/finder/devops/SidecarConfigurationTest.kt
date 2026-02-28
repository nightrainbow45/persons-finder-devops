package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for PII redaction sidecar configuration in the Deployment template.
 * Validates: Requirements 5.4, 8.2
 *
 * Verifies that the sidecar container is properly defined in the deployment template
 * with conditional rendering, parameterized image, localhost communication, and
 * resource configuration from values.yaml.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SidecarConfigurationTest {

    private lateinit var deploymentContent: String
    private lateinit var valuesContent: String
    private lateinit var valuesDevContent: String
    private lateinit var valuesProdContent: String

    @BeforeAll
    fun setup() {
        val basePath = Paths.get(System.getProperty("user.dir"), "devops", "helm", "persons-finder")

        val deploymentPath = basePath.resolve("templates/deployment.yaml")
        assertTrue(Files.isRegularFile(deploymentPath), "deployment.yaml should exist")
        deploymentContent = Files.readString(deploymentPath)

        val valuesPath = basePath.resolve("values.yaml")
        assertTrue(Files.isRegularFile(valuesPath), "values.yaml should exist")
        valuesContent = Files.readString(valuesPath)

        val valuesDevPath = basePath.resolve("values-dev.yaml")
        assertTrue(Files.isRegularFile(valuesDevPath), "values-dev.yaml should exist")
        valuesDevContent = Files.readString(valuesDevPath)

        val valuesProdPath = basePath.resolve("values-prod.yaml")
        assertTrue(Files.isRegularFile(valuesProdPath), "values-prod.yaml should exist")
        valuesProdContent = Files.readString(valuesProdPath)
    }

    // --- Sidecar conditional rendering ---

    @Test
    fun `sidecar container should be conditional on sidecar enabled flag`() {
        assertTrue(
            deploymentContent.contains(".Values.sidecar.enabled"),
            "Sidecar container should be conditional on .Values.sidecar.enabled"
        )
    }

    @Test
    fun `sidecar container should be named pii-redaction-sidecar`() {
        assertTrue(
            deploymentContent.contains("name: pii-redaction-sidecar"),
            "Sidecar container should be named pii-redaction-sidecar"
        )
    }

    @Test
    fun `sidecar should not render when disabled - values yaml defaults to disabled`() {
        assertTrue(
            valuesContent.contains("enabled: false") || valuesContent.lines().any {
                it.trim().startsWith("enabled:") && it.contains("false")
            },
            "Default values.yaml should have sidecar.enabled: false"
        )
    }

    @Test
    fun `sidecar should render when enabled - values-prod enables sidecar`() {
        assertTrue(
            valuesProdContent.contains("enabled: true") || valuesProdContent.lines().any {
                it.trim().startsWith("enabled:") && it.contains("true")
            },
            "Production values should enable the sidecar"
        )
    }

    // --- Sidecar image parameterization ---

    @Test
    fun `sidecar image repository should be parameterized from values`() {
        assertTrue(
            deploymentContent.contains(".Values.sidecar.image.repository"),
            "Sidecar image repository should reference .Values.sidecar.image.repository"
        )
    }

    @Test
    fun `sidecar image tag should be parameterized from values`() {
        assertTrue(
            deploymentContent.contains(".Values.sidecar.image.tag"),
            "Sidecar image tag should reference .Values.sidecar.image.tag"
        )
    }

    @Test
    fun `values yaml should define sidecar image repository`() {
        assertTrue(
            valuesContent.contains("repository:") && valuesContent.contains("pii-redaction-sidecar"),
            "values.yaml should define a sidecar image repository containing 'pii-redaction-sidecar'"
        )
    }

    // --- Localhost communication setup ---

    @Test
    fun `sidecar should configure PROXY_PORT environment variable`() {
        assertTrue(
            deploymentContent.contains("PROXY_PORT"),
            "Sidecar should have PROXY_PORT environment variable for proxy listening port"
        )
    }

    @Test
    fun `sidecar should configure TARGET_HOST as localhost`() {
        assertTrue(
            deploymentContent.contains("TARGET_HOST") && deploymentContent.contains("localhost"),
            "Sidecar should have TARGET_HOST set to localhost for pod-local communication"
        )
    }

    @Test
    fun `sidecar should configure TARGET_PORT for main application`() {
        assertTrue(
            deploymentContent.contains("TARGET_PORT"),
            "Sidecar should have TARGET_PORT pointing to the main application container port"
        )
    }

    @Test
    fun `sidecar port should be parameterized from values`() {
        assertTrue(
            deploymentContent.contains(".Values.sidecar.port"),
            "Sidecar port should reference .Values.sidecar.port"
        )
    }

    // --- Sidecar resource configuration ---

    @Test
    fun `sidecar should reference resources from values`() {
        assertTrue(
            deploymentContent.contains(".Values.sidecar.resources"),
            "Sidecar resources should reference .Values.sidecar.resources"
        )
    }

    @Test
    fun `values yaml should define sidecar resource limits`() {
        // Check that sidecar section in values.yaml has resource limits
        val sidecarSection = extractSidecarSection(valuesContent)
        assertTrue(
            sidecarSection.contains("limits:") && sidecarSection.contains("cpu:") && sidecarSection.contains("memory:"),
            "values.yaml sidecar section should define resource limits for cpu and memory"
        )
    }

    @Test
    fun `values yaml should define sidecar resource requests`() {
        val sidecarSection = extractSidecarSection(valuesContent)
        assertTrue(
            sidecarSection.contains("requests:") && sidecarSection.contains("cpu:") && sidecarSection.contains("memory:"),
            "values.yaml sidecar section should define resource requests for cpu and memory"
        )
    }

    @Test
    fun `values yaml should define sidecar port`() {
        val sidecarSection = extractSidecarSection(valuesContent)
        assertTrue(
            sidecarSection.contains("port:"),
            "values.yaml sidecar section should define a port"
        )
    }

    // --- Dev environment sidecar disabled ---

    @Test
    fun `dev environment should have sidecar disabled`() {
        // values-dev.yaml should explicitly disable sidecar or not enable it
        val sidecarSection = extractSidecarSection(valuesDevContent)
        assertTrue(
            sidecarSection.contains("enabled: false"),
            "values-dev.yaml should have sidecar.enabled: false"
        )
    }

    /**
     * Extracts the sidecar section from a values YAML content string.
     * Looks for lines starting with "sidecar:" and captures until the next top-level key.
     */
    private fun extractSidecarSection(content: String): String {
        val lines = content.lines()
        val startIndex = lines.indexOfFirst { it.trimEnd() == "sidecar:" || it.startsWith("sidecar:") }
        if (startIndex == -1) return ""

        val sb = StringBuilder()
        sb.appendLine(lines[startIndex])
        for (i in (startIndex + 1) until lines.size) {
            val line = lines[i]
            // Stop at next top-level key (non-indented, non-empty, non-comment)
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t") && !line.startsWith("#")) {
                break
            }
            sb.appendLine(line)
        }
        return sb.toString()
    }
}
