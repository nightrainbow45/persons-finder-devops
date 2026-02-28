package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for Helm Chart structure.
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.8
 *
 * Verifies that the Helm chart at devops/helm/persons-finder/ has the correct
 * structure, required metadata fields, environment-specific values files,
 * expected templates, and passes helm lint validation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelmChartStructureTest {

    private val projectRoot = Paths.get(System.getProperty("user.dir"))
    private val chartDir = projectRoot.resolve("devops/helm/persons-finder")
    private lateinit var chartYamlContent: String

    @BeforeAll
    fun setup() {
        val chartYamlPath = chartDir.resolve("Chart.yaml")
        assertTrue(Files.isRegularFile(chartYamlPath), "Chart.yaml must exist for tests to run")
        chartYamlContent = Files.readString(chartYamlPath)
    }

    // --- Requirement 8.1, 8.2: Chart.yaml exists and has required fields ---

    @Test
    fun `Chart yaml should exist`() {
        assertTrue(
            Files.isRegularFile(chartDir.resolve("Chart.yaml")),
            "Chart.yaml should exist at devops/helm/persons-finder/Chart.yaml"
        )
    }

    @ParameterizedTest(name = "Chart.yaml should contain required field: {0}")
    @ValueSource(strings = ["apiVersion", "name", "version", "appVersion", "description"])
    fun `Chart yaml should contain required fields`(field: String) {
        assertTrue(
            chartYamlContent.lines().any { it.trim().startsWith("$field:") },
            "Chart.yaml should contain the '$field' field"
        )
    }

    @Test
    fun `Chart yaml name should be persons-finder`() {
        val nameLine = chartYamlContent.lines().first { it.trim().startsWith("name:") }
        val name = nameLine.substringAfter(":").trim()
        assertEquals("persons-finder", name, "Chart name should be 'persons-finder'")
    }

    @Test
    fun `Chart yaml apiVersion should be v2`() {
        val apiVersionLine = chartYamlContent.lines().first { it.trim().startsWith("apiVersion:") }
        val apiVersion = apiVersionLine.substringAfter(":").trim()
        assertEquals("v2", apiVersion, "Chart apiVersion should be 'v2'")
    }

    // --- Requirement 8.3, 8.4: Values files for different environments ---

    @ParameterizedTest(name = "Values file {0} should exist")
    @ValueSource(strings = ["values.yaml", "values-dev.yaml", "values-prod.yaml"])
    fun `values files should exist for different environments`(fileName: String) {
        assertTrue(
            Files.isRegularFile(chartDir.resolve(fileName)),
            "$fileName should exist at devops/helm/persons-finder/$fileName"
        )
    }

    // --- Requirement 8.2: Templates directory structure ---

    @Test
    fun `templates directory should exist`() {
        assertTrue(
            Files.isDirectory(chartDir.resolve("templates")),
            "templates/ directory should exist in the Helm chart"
        )
    }

    @ParameterizedTest(name = "Template file {0} should exist")
    @ValueSource(strings = [
        "_helpers.tpl",
        "NOTES.txt",
        "deployment.yaml",
        "service.yaml",
        "ingress.yaml",
        "hpa.yaml",
        "secret.yaml"
    ])
    fun `expected template files should exist`(templateFile: String) {
        assertTrue(
            Files.isRegularFile(chartDir.resolve("templates/$templateFile")),
            "templates/$templateFile should exist in the Helm chart"
        )
    }

    // --- .helmignore ---

    @Test
    fun `helmignore file should exist`() {
        assertTrue(
            Files.isRegularFile(chartDir.resolve(".helmignore")),
            ".helmignore should exist at devops/helm/persons-finder/.helmignore"
        )
    }

    // --- Requirement 8.8: Helm lint validation ---

    @Test
    fun `helm lint should pass on the chart`() {
        // Skip if helm is not installed
        val helmAvailable = try {
            val process = ProcessBuilder("helm", "version", "--short")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
        Assumptions.assumeTrue(helmAvailable, "Skipping helm lint: helm CLI is not available")

        val process = ProcessBuilder("helm", "lint", chartDir.toString())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertEquals(0, exitCode, "helm lint should pass without errors. Output:\n$output")
    }
}
