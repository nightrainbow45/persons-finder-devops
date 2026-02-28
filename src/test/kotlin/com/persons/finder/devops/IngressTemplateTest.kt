package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for the Kubernetes Ingress template.
 * Validates: Requirements 3.7, 8.2
 *
 * Parses the Helm template file at devops/helm/persons-finder/templates/ingress.yaml
 * and verifies the template defines the correct Kubernetes Ingress resource structure,
 * conditional rendering based on ingress.enabled, host and path parameterization,
 * TLS configuration, ingressClassName support, annotations support, and service port
 * reference for backend configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngressTemplateTest {

    private lateinit var templateContent: String
    private lateinit var templateLines: List<String>

    @BeforeAll
    fun setup() {
        val templatePath = Paths.get(
            System.getProperty("user.dir"),
            "devops", "helm", "persons-finder", "templates", "ingress.yaml"
        )
        assertTrue(Files.isRegularFile(templatePath), "ingress.yaml should exist at devops/helm/persons-finder/templates/ingress.yaml")
        templateContent = Files.readString(templatePath)
        templateLines = templateContent.lines()
    }

    // --- Requirement 3.7: Template has conditional rendering based on .Values.ingress.enabled ---

    @Test
    fun `template should have conditional rendering based on ingress enabled`() {
        assertTrue(
            templateContent.contains("if .Values.ingress.enabled"),
            "Template should be conditionally rendered based on .Values.ingress.enabled"
        )
    }

    @Test
    fun `template should close the conditional block`() {
        val openCount = templateContent.lines().count { it.trim().startsWith("{{- if") }
        val endCount = templateContent.lines().count { it.trim().startsWith("{{- end") || it.trim() == "{{- end }}" }
        assertTrue(
            endCount >= openCount,
            "Template should close all conditional blocks (found $openCount if-blocks and $endCount end-blocks)"
        )
    }

    // --- Requirement 3.7: Template defines apiVersion: networking.k8s.io/v1 and kind: Ingress ---

    @Test
    fun `template should define apiVersion networking k8s io v1`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("apiVersion:") && it.contains("networking.k8s.io/v1") },
            "Ingress template should define apiVersion: networking.k8s.io/v1"
        )
    }

    @Test
    fun `template should define kind Ingress`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("kind:") && it.contains("Ingress") },
            "Template should define kind: Ingress"
        )
    }

    // --- Requirement 3.7: Template references .Values.ingress.hosts for host parameterization ---

    @Test
    fun `template should reference Values ingress hosts for host parameterization`() {
        assertTrue(
            templateContent.contains(".Values.ingress.hosts"),
            "Template should reference .Values.ingress.hosts for host configuration"
        )
    }

    @Test
    fun `template should iterate over hosts using range`() {
        assertTrue(
            templateContent.contains("range .Values.ingress.hosts"),
            "Template should iterate over .Values.ingress.hosts using range"
        )
    }

    @Test
    fun `template should reference host field for each host entry`() {
        assertTrue(
            templateContent.contains(".host"),
            "Template should reference .host for each host entry"
        )
    }

    @Test
    fun `template should reference path and pathType for each path entry`() {
        assertTrue(
            templateContent.contains(".path"),
            "Template should reference .path for each path entry"
        )
        assertTrue(
            templateContent.contains(".pathType"),
            "Template should reference .pathType for each path entry"
        )
    }

    // --- Requirement 3.7: Template references .Values.ingress.tls for TLS configuration ---

    @Test
    fun `template should reference Values ingress tls for TLS configuration`() {
        assertTrue(
            templateContent.contains(".Values.ingress.tls"),
            "Template should reference .Values.ingress.tls for TLS configuration"
        )
    }

    @Test
    fun `TLS section should be conditional on ingress tls being set`() {
        assertTrue(
            templateContent.contains("if .Values.ingress.tls"),
            "TLS section should be conditionally rendered based on .Values.ingress.tls"
        )
    }

    @Test
    fun `TLS section should reference secretName`() {
        assertTrue(
            templateContent.contains(".secretName"),
            "TLS section should reference .secretName for the TLS certificate secret"
        )
    }

    @Test
    fun `TLS section should reference hosts`() {
        assertTrue(
            templateContent.contains("range .hosts"),
            "TLS section should iterate over .hosts for TLS host entries"
        )
    }

    // --- Requirement 8.2: Template supports ingressClassName from values ---

    @Test
    fun `template should support ingressClassName from values`() {
        assertTrue(
            templateContent.contains(".Values.ingress.className"),
            "Template should reference .Values.ingress.className for ingress class configuration"
        )
    }

    @Test
    fun `ingressClassName should be conditional`() {
        assertTrue(
            templateContent.contains("if .Values.ingress.className"),
            "ingressClassName should be conditionally rendered"
        )
    }

    // --- Requirement 8.2: Template supports annotations from values ---

    @Test
    fun `template should support annotations from values`() {
        assertTrue(
            templateContent.contains(".Values.ingress.annotations"),
            "Template should reference .Values.ingress.annotations for configurable annotations"
        )
    }

    // --- Requirement 8.2: Template references service port for backend ---

    @Test
    fun `template should reference service port for backend configuration`() {
        assertTrue(
            templateContent.contains(".Values.service.port"),
            "Template should reference .Values.service.port for backend service port number"
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
