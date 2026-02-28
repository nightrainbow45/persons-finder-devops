package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for CI/CD workflow configuration.
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7
 *
 * Parses the GitHub Actions workflow at devops/ci/ci-cd.yml and verifies
 * trigger configuration, build/test stages, security scanning, and
 * container registry push configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CiCdWorkflowTest {

    private lateinit var workflowContent: String
    private lateinit var workflowLines: List<String>

    @BeforeAll
    fun setup() {
        val workflowPath = Paths.get(System.getProperty("user.dir"), "devops", "ci", "ci-cd.yml")
        assertTrue(Files.isRegularFile(workflowPath), "CI/CD workflow should exist at devops/ci/ci-cd.yml")
        workflowContent = Files.readString(workflowPath)
        workflowLines = workflowContent.lines()
    }

    // --- Requirement 6.7: Workflow file exists and is valid YAML structure ---

    @Test
    fun `workflow file should exist at devops ci ci-cd yml`() {
        val workflowPath = Paths.get(System.getProperty("user.dir"), "devops", "ci", "ci-cd.yml")
        assertTrue(Files.isRegularFile(workflowPath), "Workflow file must exist at devops/ci/ci-cd.yml")
    }

    @Test
    fun `workflow should have a name defined`() {
        assertTrue(
            workflowContent.contains("name:"),
            "Workflow should have a name defined"
        )
    }

    // --- Requirement 6.7: Trigger configuration ---

    @Test
    fun `workflow should trigger on push to main branch`() {
        assertTrue(
            workflowContent.contains("push:") && workflowContent.contains("main"),
            "Workflow should trigger on push to main branch"
        )
    }

    @Test
    fun `workflow should have on trigger section with branches`() {
        assertTrue(
            workflowContent.contains("on:") && workflowContent.contains("branches:"),
            "Workflow should have an 'on' trigger section with branches configuration"
        )
    }

    // --- Requirement 6.1: Build with Gradle ---

    @Test
    fun `workflow should have build-and-test job`() {
        assertTrue(
            workflowContent.contains("build-and-test:"),
            "Workflow should define a 'build-and-test' job"
        )
    }

    @Test
    fun `build job should include Gradle build step`() {
        assertTrue(
            workflowContent.contains("gradlew") && workflowContent.contains("build"),
            "Build job should run Gradle build (gradlew build)"
        )
    }

    // --- Requirement 6.2: Run unit tests ---

    @Test
    fun `build job should include Gradle test step`() {
        assertTrue(
            workflowContent.contains("gradlew test"),
            "Build job should run Gradle tests (gradlew test)"
        )
    }

    @Test
    fun `build job should configure JDK 11`() {
        assertTrue(
            workflowContent.contains("java-version") && workflowContent.contains("'11'"),
            "Build job should set up JDK 11"
        )
    }

    @Test
    fun `build job should upload test results as artifacts`() {
        assertTrue(
            workflowContent.contains("upload-artifact") && workflowContent.contains("test-results"),
            "Build job should upload test results as artifacts"
        )
    }

    // --- Requirement 6.3: Docker build and AWS OIDC ---

    @Test
    fun `workflow should have docker build job`() {
        assertTrue(
            workflowContent.contains("docker-build-and-scan:"),
            "Workflow should define a 'docker-build-and-scan' job"
        )
    }

    @Test
    fun `docker job should configure AWS OIDC authentication`() {
        assertTrue(
            workflowContent.contains("aws-actions/configure-aws-credentials"),
            "Docker job should use aws-actions/configure-aws-credentials for OIDC authentication"
        )
    }

    @Test
    fun `docker job should configure role-to-assume for OIDC`() {
        assertTrue(
            workflowContent.contains("role-to-assume"),
            "AWS credentials action should configure role-to-assume for OIDC"
        )
    }

    // --- Requirement 6.4, 6.5: Security scanning with Trivy ---

    @Test
    fun `docker job should include Trivy security scanner`() {
        assertTrue(
            workflowContent.contains("aquasecurity/trivy-action"),
            "Docker job should use aquasecurity/trivy-action for security scanning"
        )
    }

    @Test
    fun `Trivy should be configured to fail on HIGH or CRITICAL severity`() {
        assertTrue(
            workflowContent.contains("CRITICAL") && workflowContent.contains("HIGH"),
            "Trivy should be configured with CRITICAL and HIGH severity levels"
        )
        assertTrue(
            workflowContent.contains("exit-code: '1'"),
            "Trivy should be configured with exit-code 1 to fail the build on vulnerabilities"
        )
    }

    // --- Requirement 6.6: Push to container registry ---

    @Test
    fun `docker job should configure ECR login`() {
        assertTrue(
            workflowContent.contains("aws-actions/amazon-ecr-login"),
            "Docker job should use aws-actions/amazon-ecr-login for ECR authentication"
        )
    }

    @Test
    fun `docker job should push image to ECR on main branch`() {
        assertTrue(
            workflowContent.contains("push: true"),
            "Docker job should push the image to ECR"
        )
        assertTrue(
            workflowContent.contains("refs/heads/main"),
            "ECR push should be conditional on main branch"
        )
    }
}
