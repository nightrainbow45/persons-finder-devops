package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for the Kubernetes Service template.
 * Validates: Requirements 3.6, 8.2
 *
 * Parses the Helm template file at devops/helm/persons-finder/templates/service.yaml
 * and verifies the template defines the correct Kubernetes Service resource structure,
 * parameterization of service type and port, selector labels, annotations support,
 * and TCP protocol configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceTemplateTest {

    private lateinit var templateContent: String
    private lateinit var templateLines: List<String>

    @BeforeAll
    fun setup() {
        val templatePath = Paths.get(
            System.getProperty("user.dir"),
            "devops", "helm", "persons-finder", "templates", "service.yaml"
        )
        assertTrue(Files.isRegularFile(templatePath), "service.yaml should exist at devops/helm/persons-finder/templates/service.yaml")
        templateContent = Files.readString(templatePath)
        templateLines = templateContent.lines()
    }

    // --- Requirement 3.6: Template defines apiVersion: v1 and kind: Service ---

    @Test
    fun `template should define apiVersion v1`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("apiVersion:") && it.contains("v1") },
            "Service template should define apiVersion: v1"
        )
    }

    @Test
    fun `template should define kind Service`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("kind:") && it.contains("Service") },
            "Template should define kind: Service"
        )
    }

    // --- Requirement 8.2: Template references .Values.service.type for service type parameterization ---

    @Test
    fun `template should reference Values service type for service type parameterization`() {
        assertTrue(
            templateContent.contains(".Values.service.type"),
            "Template should reference .Values.service.type for parameterized service type (ClusterIP/LoadBalancer/NodePort)"
        )
    }

    // --- Requirement 8.2: Template references .Values.service.port for port configuration ---

    @Test
    fun `template should reference Values service port for port configuration`() {
        assertTrue(
            templateContent.contains(".Values.service.port"),
            "Template should reference .Values.service.port for parameterized port configuration"
        )
    }

    // --- Requirement 3.6: Template uses persons-finder selectorLabels for selector ---

    @Test
    fun `template should use persons-finder selectorLabels for selector`() {
        assertTrue(
            templateContent.contains("persons-finder.selectorLabels"),
            "Template should use persons-finder.selectorLabels helper for selector to match Deployment labels"
        )
    }

    @Test
    fun `selector should be under spec section`() {
        val selectorIndex = templateLines.indexOfFirst { it.trim().startsWith("selector:") }
        assertTrue(selectorIndex >= 0, "Template should have a selector field")
        // Verify selector appears after spec
        val specIndex = templateLines.indexOfFirst { it.trim().startsWith("spec:") }
        assertTrue(specIndex >= 0, "Template should have a spec field")
        assertTrue(selectorIndex > specIndex, "selector should appear under spec section")
    }

    // --- Requirement 3.6: Template supports annotations from values ---

    @Test
    fun `template should support service annotations from values`() {
        assertTrue(
            templateContent.contains(".Values.service.annotations"),
            "Template should reference .Values.service.annotations for configurable annotations"
        )
    }

    // --- Requirement 3.6: Template defines port with protocol TCP ---

    @Test
    fun `template should define port with protocol TCP`() {
        assertTrue(
            templateContent.contains("protocol: TCP"),
            "Template should define port with protocol: TCP"
        )
    }

    @Test
    fun `template should define port with targetPort http`() {
        assertTrue(
            templateContent.contains("targetPort: http"),
            "Template should define port with targetPort: http"
        )
    }

    @Test
    fun `template should define port with name http`() {
        assertTrue(
            templateLines.any { it.trim() == "name: http" },
            "Template should define port with name: http"
        )
    }

    // --- Requirement 8.2: Template uses Helm helpers for metadata ---

    @Test
    fun `template should use fullname helper for metadata name`() {
        assertTrue(
            templateContent.contains("persons-finder.fullname"),
            "Template should use persons-finder.fullname helper for metadata name"
        )
    }

    @Test
    fun `template should use labels helper for metadata labels`() {
        assertTrue(
            templateContent.contains("persons-finder.labels"),
            "Template should use persons-finder.labels helper for metadata labels"
        )
    }
}
