package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for the Kubernetes Deployment template.
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 8.2
 *
 * Parses the Helm template file at devops/helm/persons-finder/templates/deployment.yaml
 * and verifies the template defines the correct Kubernetes resource structure,
 * parameterization of replicas, resources, probes, security context, and environment
 * variable injection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeploymentTemplateTest {

    private lateinit var templateContent: String
    private lateinit var templateLines: List<String>

    @BeforeAll
    fun setup() {
        val templatePath = Paths.get(
            System.getProperty("user.dir"),
            "devops", "helm", "persons-finder", "templates", "deployment.yaml"
        )
        assertTrue(Files.isRegularFile(templatePath), "deployment.yaml should exist at devops/helm/persons-finder/templates/deployment.yaml")
        templateContent = Files.readString(templatePath)
        templateLines = templateContent.lines()
    }

    // --- Requirement 3.1: Template defines kind: Deployment with apiVersion: apps/v1 ---

    @Test
    fun `template should define apiVersion apps v1`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("apiVersion:") && it.contains("apps/v1") },
            "Deployment template should define apiVersion: apps/v1"
        )
    }

    @Test
    fun `template should define kind Deployment`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("kind:") && it.contains("Deployment") },
            "Template should define kind: Deployment"
        )
    }

    // --- Requirement 3.2: Template references .Values.replicaCount ---

    @Test
    fun `template should reference Values replicaCount for replica count`() {
        assertTrue(
            templateContent.contains(".Values.replicaCount"),
            "Template should reference .Values.replicaCount for parameterized replica count"
        )
    }

    @Test
    fun `replica count should be conditional on autoscaling not enabled`() {
        // The template should only set replicas when autoscaling is not enabled
        assertTrue(
            templateContent.contains("if not .Values.autoscaling.enabled"),
            "Replica count should be conditional on autoscaling not being enabled"
        )
    }

    // --- Requirement 3.5: Template references .Values.resources ---

    @Test
    fun `template should reference Values resources for resource configuration`() {
        assertTrue(
            templateContent.contains(".Values.resources"),
            "Template should reference .Values.resources for CPU and memory requests/limits"
        )
    }

    // --- Requirement 2.2: Template includes envFrom with secretRef ---

    @Test
    fun `template should include envFrom with secretRef for environment variable injection`() {
        assertTrue(
            templateContent.contains("envFrom"),
            "Template should include envFrom for environment variable injection"
        )
        assertTrue(
            templateContent.contains("secretRef"),
            "Template should include secretRef under envFrom for secret-based env injection"
        )
    }

    @Test
    fun `envFrom secretRef should always be present regardless of secrets create`() {
        // secrets.create controls whether Helm creates the Secret resource,
        // NOT whether the pod mounts it. The pod always needs the secret (ESO creates it).
        assertTrue(
            templateContent.contains("envFrom"),
            "envFrom should always be present so ESO-managed secrets are injected"
        )
        assertFalse(
            templateContent.contains("{{- if .Values.secrets.create }}\n        envFrom"),
            "envFrom must NOT be gated on secrets.create; that flag only controls Secret resource creation"
        )
    }

    // --- Requirement 3.3, 3.4: Template includes liveness and readiness probes ---

    @Test
    fun `template should include livenessProbe from Values probes`() {
        assertTrue(
            templateContent.contains("livenessProbe"),
            "Template should include livenessProbe configuration"
        )
        assertTrue(
            templateContent.contains(".Values.probes.liveness"),
            "livenessProbe should reference .Values.probes.liveness for parameterization"
        )
    }

    @Test
    fun `template should include readinessProbe from Values probes`() {
        assertTrue(
            templateContent.contains("readinessProbe"),
            "Template should include readinessProbe configuration"
        )
        assertTrue(
            templateContent.contains(".Values.probes.readiness"),
            "readinessProbe should reference .Values.probes.readiness for parameterization"
        )
    }

    // --- Requirement 3.1: Template includes securityContext ---

    @Test
    fun `template should include pod-level securityContext`() {
        assertTrue(
            templateContent.contains(".Values.podSecurityContext"),
            "Template should reference .Values.podSecurityContext for pod-level security context"
        )
    }

    @Test
    fun `template should include container-level securityContext`() {
        assertTrue(
            templateContent.contains(".Values.securityContext"),
            "Template should reference .Values.securityContext for container-level security context"
        )
    }

    // --- Requirement 3.1: Template uses RollingUpdate strategy ---

    @Test
    fun `template should use RollingUpdate strategy`() {
        assertTrue(
            templateContent.contains("type: RollingUpdate"),
            "Template should define strategy type: RollingUpdate"
        )
    }

    @Test
    fun `RollingUpdate should configure maxSurge and maxUnavailable`() {
        assertTrue(
            templateContent.contains("maxSurge:"),
            "RollingUpdate strategy should configure maxSurge"
        )
        assertTrue(
            templateContent.contains("maxUnavailable:"),
            "RollingUpdate strategy should configure maxUnavailable"
        )
    }

    // --- Requirement 8.2: Template references image repository and tag from values ---

    @Test
    fun `template should reference image repository from values`() {
        assertTrue(
            templateContent.contains(".Values.image.repository"),
            "Template should reference .Values.image.repository for the container image"
        )
    }

    @Test
    fun `template should reference image tag from values`() {
        assertTrue(
            templateContent.contains(".Values.image.tag"),
            "Template should reference .Values.image.tag for the container image tag"
        )
    }

    @Test
    fun `template should reference image pullPolicy from values`() {
        assertTrue(
            templateContent.contains(".Values.image.pullPolicy"),
            "Template should reference .Values.image.pullPolicy"
        )
    }
}
