package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for Dockerfile best practices.
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 *
 * Parses the Dockerfile at devops/docker/Dockerfile and verifies
 * multi-stage build structure, non-root user, version pinning,
 * port exposure, and health check configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DockerfileBestPracticesTest {

    private lateinit var dockerfileLines: List<String>
    private lateinit var dockerfileContent: String

    @BeforeAll
    fun setup() {
        val dockerfilePath = Paths.get(System.getProperty("user.dir"), "devops", "docker", "Dockerfile")
        assertTrue(Files.isRegularFile(dockerfilePath), "Dockerfile should exist at devops/docker/Dockerfile")
        dockerfileContent = Files.readString(dockerfilePath)
        dockerfileLines = dockerfileContent.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
    }

    // --- Requirement 1.2: Multi-stage build ---

    @Test
    fun `Dockerfile should use multi-stage build with at least two FROM statements`() {
        val fromStatements = dockerfileLines.filter { it.uppercase().startsWith("FROM ") }
        assertTrue(
            fromStatements.size >= 2,
            "Multi-stage build requires at least 2 FROM statements, found ${fromStatements.size}"
        )
    }

    @Test
    fun `build stage should use gradle JDK image`() {
        val fromStatements = dockerfileLines.filter { it.uppercase().startsWith("FROM ") }
        val buildStage = fromStatements.first()
        assertTrue(
            buildStage.lowercase().contains("gradle") && buildStage.lowercase().contains("jdk"),
            "Build stage should use a gradle JDK image, found: $buildStage"
        )
    }

    @Test
    fun `runtime stage should use a JRE alpine image`() {
        val fromStatements = dockerfileLines.filter { it.uppercase().startsWith("FROM ") }
        assertTrue(fromStatements.size >= 2, "Need at least 2 FROM statements for multi-stage build")
        val runtimeStage = fromStatements.last()
        assertTrue(
            runtimeStage.lowercase().contains("jre") && runtimeStage.lowercase().contains("alpine"),
            "Runtime stage should use a JRE alpine image for minimal size, found: $runtimeStage"
        )
    }

    // --- Requirement 1.3: Non-root user ---

    @Test
    fun `Dockerfile should contain USER instruction`() {
        val userInstructions = dockerfileLines.filter { it.uppercase().startsWith("USER ") }
        assertTrue(
            userInstructions.isNotEmpty(),
            "Dockerfile should contain at least one USER instruction for security"
        )
    }

    @Test
    fun `Dockerfile should not run as root user`() {
        val userInstructions = dockerfileLines.filter { it.uppercase().startsWith("USER ") }
        assertTrue(userInstructions.isNotEmpty(), "USER instruction must be present")
        val lastUser = userInstructions.last().split("\\s+".toRegex()).last()
        assertNotEquals(
            "root", lastUser.lowercase(),
            "Container should not run as root user, found USER $lastUser"
        )
    }

    // --- Requirement 1.4: Base image version pinning ---

    @Test
    fun `base images should not use latest tag`() {
        val fromStatements = dockerfileLines.filter { it.uppercase().startsWith("FROM ") }
        for (from in fromStatements) {
            val image = from.split("\\s+".toRegex())[1]
            assertFalse(
                image.endsWith(":latest"),
                "Base image should not use :latest tag: $image"
            )
        }
    }

    @Test
    fun `base images should have explicit version tags`() {
        val fromStatements = dockerfileLines.filter { it.uppercase().startsWith("FROM ") }
        for (from in fromStatements) {
            val image = from.split("\\s+".toRegex())[1]
            assertTrue(
                image.contains(":"),
                "Base image should have an explicit version tag (no implicit :latest): $image"
            )
        }
    }

    // --- Requirement 1.5: Port 8080 exposure ---

    @Test
    fun `Dockerfile should expose port 8080`() {
        val exposeStatements = dockerfileLines.filter { it.uppercase().startsWith("EXPOSE ") }
        val exposedPorts = exposeStatements.flatMap { it.split("\\s+".toRegex()).drop(1) }
        assertTrue(
            exposedPorts.any { it == "8080" },
            "Dockerfile should expose port 8080 for Spring Boot, found exposed ports: $exposedPorts"
        )
    }

    // --- Requirement 1.6: Health check ---

    @Test
    fun `Dockerfile should include HEALTHCHECK instruction`() {
        val healthcheckPresent = dockerfileContent.uppercase().contains("HEALTHCHECK")
        assertTrue(
            healthcheckPresent,
            "Dockerfile should include a HEALTHCHECK instruction for container health monitoring"
        )
    }
}
