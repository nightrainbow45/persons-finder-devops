package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for the Kubernetes Secret Helm template.
 * Validates: Requirements 2.1, 2.5
 *
 * Parses the Secret template at devops/helm/persons-finder/templates/secret.yaml
 * and verifies conditional rendering, Secret kind, Opaque type, OPENAI_API_KEY
 * reference, base64 encoding, and manual creation documentation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecretTemplateTest {

    private lateinit var templateContent: String
    private lateinit var templateLines: List<String>

    @BeforeAll
    fun setup() {
        val templatePath = Paths.get(
            System.getProperty("user.dir"),
            "devops", "helm", "persons-finder", "templates", "secret.yaml"
        )
        assertTrue(Files.isRegularFile(templatePath), "secret.yaml should exist at devops/helm/persons-finder/templates/secret.yaml")
        templateContent = Files.readString(templatePath)
        templateLines = templateContent.lines()
    }

    // --- Requirement 2.5: Secret created separately, conditional rendering ---

    @Test
    fun `template should have conditional rendering on secrets create`() {
        assertTrue(
            templateContent.contains("{{- if .Values.secrets.create }}"),
            "Secret template should be conditionally rendered based on .Values.secrets.create"
        )
    }

    @Test
    fun `template should close the conditional block`() {
        val openCount = templateContent.split("{{- if ").size - 1
        val endCount = templateContent.split("{{- end }}").size - 1
        assertTrue(
            endCount >= openCount,
            "All conditional blocks should be properly closed (found $openCount opens, $endCount ends)"
        )
    }

    // --- Secret kind and type ---

    @Test
    fun `template should define kind as Secret`() {
        val kindLine = templateLines.firstOrNull { it.trim().startsWith("kind:") }
        assertNotNull(kindLine, "Template should contain a 'kind' field")
        assertTrue(
            kindLine!!.trim() == "kind: Secret",
            "kind should be 'Secret', found: ${kindLine.trim()}"
        )
    }

    @Test
    fun `template should define type as Opaque`() {
        val typeLine = templateLines.firstOrNull { it.trim().startsWith("type:") }
        assertNotNull(typeLine, "Template should contain a 'type' field")
        assertTrue(
            typeLine!!.trim() == "type: Opaque",
            "type should be 'Opaque', found: ${typeLine.trim()}"
        )
    }

    // --- Requirement 2.1: API key not baked in, managed via Secret ---

    @Test
    fun `template should reference OPENAI_API_KEY`() {
        assertTrue(
            templateContent.contains("OPENAI_API_KEY"),
            "Secret template should reference OPENAI_API_KEY"
        )
    }

    @Test
    fun `template should use b64enc for encoding the API key`() {
        assertTrue(
            templateContent.contains("b64enc"),
            "Secret template should use b64enc to base64-encode the API key value"
        )
    }

    @Test
    fun `template should have conditional check for OPENAI_API_KEY value`() {
        assertTrue(
            templateContent.contains(".Values.secrets.OPENAI_API_KEY"),
            "Template should conditionally check if OPENAI_API_KEY is provided in values"
        )
    }

    // --- Documentation for manual secret creation ---

    @Test
    fun `template should include documentation for manual secret creation`() {
        val hasManualDoc = templateContent.contains("created manually") ||
            templateContent.contains("create secret") ||
            templateContent.contains("kubectl create secret")
        assertTrue(
            hasManualDoc,
            "Template should include documentation or comments about manual secret creation"
        )
    }

    // --- Structural checks ---

    @Test
    fun `template should define apiVersion v1`() {
        val apiVersionLine = templateLines.firstOrNull { it.trim().startsWith("apiVersion:") }
        assertNotNull(apiVersionLine, "Template should contain an 'apiVersion' field")
        assertTrue(
            apiVersionLine!!.trim() == "apiVersion: v1",
            "apiVersion should be 'v1', found: ${apiVersionLine.trim()}"
        )
    }

    @Test
    fun `template should include metadata with name`() {
        assertTrue(
            templateContent.contains("metadata:"),
            "Template should include a metadata section"
        )
        assertTrue(
            templateContent.contains("name:"),
            "Template metadata should include a name field"
        )
    }
}
