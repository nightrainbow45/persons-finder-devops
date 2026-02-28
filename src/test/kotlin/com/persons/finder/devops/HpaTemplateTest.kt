package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for the Kubernetes HorizontalPodAutoscaler template.
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 8.2
 *
 * Parses the Helm template file at devops/helm/persons-finder/templates/hpa.yaml
 * and verifies the template defines the correct HPA resource structure,
 * conditional rendering based on autoscaling.enabled, minReplicas/maxReplicas
 * parameterization, CPU target utilization, and stabilization window configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HpaTemplateTest {

    private lateinit var templateContent: String
    private lateinit var templateLines: List<String>

    @BeforeAll
    fun setup() {
        val templatePath = Paths.get(
            System.getProperty("user.dir"),
            "devops", "helm", "persons-finder", "templates", "hpa.yaml"
        )
        assertTrue(Files.isRegularFile(templatePath), "hpa.yaml should exist at devops/helm/persons-finder/templates/hpa.yaml")
        templateContent = Files.readString(templatePath)
        templateLines = templateContent.lines()
    }

    // --- Requirement 4.1: Template has conditional rendering based on .Values.autoscaling.enabled ---

    @Test
    fun `template should have conditional rendering based on autoscaling enabled`() {
        assertTrue(
            templateContent.contains("if .Values.autoscaling.enabled"),
            "Template should be conditionally rendered based on .Values.autoscaling.enabled"
        )
    }

    @Test
    fun `template should close the conditional block`() {
        val openCount = templateLines.count { it.trim().startsWith("{{- if") }
        val endCount = templateLines.count { it.trim().startsWith("{{- end") || it.trim() == "{{- end }}" }
        assertTrue(
            endCount >= openCount,
            "Template should close all conditional blocks (found $openCount if-blocks and $endCount end-blocks)"
        )
    }

    // --- Requirement 4.1: Template defines apiVersion: autoscaling/v2 and kind: HorizontalPodAutoscaler ---

    @Test
    fun `template should define apiVersion autoscaling v2`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("apiVersion:") && it.contains("autoscaling/v2") },
            "HPA template should define apiVersion: autoscaling/v2"
        )
    }

    @Test
    fun `template should define kind HorizontalPodAutoscaler`() {
        assertTrue(
            templateLines.any { it.trim().startsWith("kind:") && it.contains("HorizontalPodAutoscaler") },
            "Template should define kind: HorizontalPodAutoscaler"
        )
    }

    // --- Requirement 4.4, 4.5: Template references .Values.autoscaling.minReplicas and maxReplicas ---

    @Test
    fun `template should reference Values autoscaling minReplicas`() {
        assertTrue(
            templateContent.contains(".Values.autoscaling.minReplicas"),
            "Template should reference .Values.autoscaling.minReplicas for minimum replica count"
        )
    }

    @Test
    fun `template should reference Values autoscaling maxReplicas`() {
        assertTrue(
            templateContent.contains(".Values.autoscaling.maxReplicas"),
            "Template should reference .Values.autoscaling.maxReplicas for maximum replica count"
        )
    }

    // --- Requirement 4.2, 4.3: Template references .Values.autoscaling.targetCPUUtilizationPercentage ---

    @Test
    fun `template should reference Values autoscaling targetCPUUtilizationPercentage`() {
        assertTrue(
            templateContent.contains(".Values.autoscaling.targetCPUUtilizationPercentage"),
            "Template should reference .Values.autoscaling.targetCPUUtilizationPercentage for CPU target"
        )
    }

    @Test
    fun `template should define CPU metric type as Resource`() {
        assertTrue(
            templateContent.contains("type: Resource"),
            "Template should define metric type as Resource for CPU utilization"
        )
    }

    @Test
    fun `template should target CPU resource`() {
        assertTrue(
            templateLines.any { it.trim() == "name: cpu" },
            "Template should target the cpu resource for autoscaling metric"
        )
    }

    @Test
    fun `template should use Utilization target type`() {
        assertTrue(
            templateContent.contains("type: Utilization"),
            "Template should use Utilization as the target type for CPU metric"
        )
    }

    // --- Requirement 4.1: Template includes stabilizationWindowSeconds configuration ---

    @Test
    fun `template should include scaleDown stabilizationWindowSeconds`() {
        assertTrue(
            templateContent.contains("stabilizationWindowSeconds"),
            "Template should include stabilizationWindowSeconds for scaling behavior"
        )
    }

    @Test
    fun `template should reference Values autoscaling stabilizationWindowSeconds with default`() {
        assertTrue(
            templateContent.contains(".Values.autoscaling.stabilizationWindowSeconds"),
            "Template should reference .Values.autoscaling.stabilizationWindowSeconds for configurable stabilization"
        )
        assertTrue(
            templateContent.contains("default 300"),
            "Template should provide a default value of 300 for stabilizationWindowSeconds"
        )
    }

    // --- Requirement 4.1: Template targets a Deployment resource ---

    @Test
    fun `template should target a Deployment via scaleTargetRef`() {
        assertTrue(
            templateContent.contains("scaleTargetRef"),
            "Template should include scaleTargetRef to target a resource"
        )
        assertTrue(
            templateContent.contains("kind: Deployment"),
            "scaleTargetRef should target kind: Deployment"
        )
    }

    @Test
    fun `scaleTargetRef should use fullname helper for deployment name`() {
        // The scaleTargetRef name should use the same Helm helper as the Deployment
        val scaleTargetSection = templateContent.substringAfter("scaleTargetRef").substringBefore("minReplicas")
        assertTrue(
            scaleTargetSection.contains("persons-finder.fullname"),
            "scaleTargetRef name should use persons-finder.fullname helper"
        )
    }

    // --- Requirement 4.1: Template includes both scaleDown and scaleUp behavior policies ---

    @Test
    fun `template should include behavior section`() {
        assertTrue(
            templateContent.contains("behavior:"),
            "Template should include a behavior section for scaling policies"
        )
    }

    @Test
    fun `template should include scaleDown behavior`() {
        assertTrue(
            templateContent.contains("scaleDown:"),
            "Template should include scaleDown behavior configuration"
        )
    }

    @Test
    fun `template should include scaleUp behavior`() {
        assertTrue(
            templateContent.contains("scaleUp:"),
            "Template should include scaleUp behavior configuration"
        )
    }

    @Test
    fun `scaleDown should define policies`() {
        val scaleDownSection = templateContent.substringAfter("scaleDown:").substringBefore("scaleUp:")
        assertTrue(
            scaleDownSection.contains("policies:"),
            "scaleDown should define scaling policies"
        )
        assertTrue(
            scaleDownSection.contains("type: Percent"),
            "scaleDown should include a Percent-based policy"
        )
    }

    @Test
    fun `scaleUp should define policies`() {
        val scaleUpSection = templateContent.substringAfter("scaleUp:")
        assertTrue(
            scaleUpSection.contains("policies:"),
            "scaleUp should define scaling policies"
        )
        assertTrue(
            scaleUpSection.contains("selectPolicy: Max"),
            "scaleUp should use selectPolicy: Max"
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
