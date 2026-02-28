package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for DevOps folder structure.
 * Validates: Requirements 8.1, 8.7
 *
 * Verifies that the devops/ directory follows best practices with proper
 * modularization, environment separation, and documentation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevOpsFolderStructureTest {

    private val projectRoot = Paths.get(System.getProperty("user.dir"))
    private val devopsDir = projectRoot.resolve("devops")

    // --- Top-level devops directory ---

    @Test
    fun `devops directory should exist at project root`() {
        assertTrue(
            Files.isDirectory(devopsDir),
            "devops/ directory should exist at the project root"
        )
    }

    // --- Required subdirectories ---

    @ParameterizedTest(name = "Required directory devops/{0} should exist")
    @ValueSource(strings = [
        "docker",
        "helm",
        "helm/persons-finder",
        "helm/persons-finder/templates",
        "helm/persons-finder/charts",
        "terraform",
        "terraform/modules",
        "terraform/modules/iam",
        "terraform/modules/iam/policies",
        "terraform/modules/iam/trust-policies",
        "terraform/modules/vpc",
        "terraform/modules/eks",
        "terraform/modules/ecr",
        "terraform/modules/secrets-manager",
        "terraform/environments",
        "terraform/environments/dev",
        "terraform/environments/prod",
        "scripts",
        "ci",
        "docs"
    ])
    fun `required directories should exist`(relativePath: String) {
        assertTrue(
            Files.isDirectory(devopsDir.resolve(relativePath)),
            "devops/$relativePath/ should exist"
        )
    }

    // --- README.md files in key directories ---

    @ParameterizedTest(name = "README.md should exist in devops/{0}")
    @ValueSource(strings = [
        "",
        "docker",
        "helm",
        "helm/persons-finder",
        "terraform",
        "terraform/modules/iam",
        "terraform/modules/vpc",
        "terraform/modules/eks",
        "terraform/modules/ecr",
        "terraform/modules/secrets-manager",
        "scripts",
        "ci",
        "docs"
    ])
    fun `README files should exist in key directories`(relativePath: String) {
        val dir = devopsDir.resolve(relativePath)
        assertTrue(
            Files.isRegularFile(dir.resolve("README.md")),
            "README.md should exist in devops/$relativePath/"
        )
    }

    // --- Docker-specific files ---

    @Test
    fun `Dockerfile should exist in docker directory`() {
        assertTrue(
            Files.isRegularFile(devopsDir.resolve("docker/Dockerfile")),
            "Dockerfile should exist at devops/docker/Dockerfile"
        )
    }

    @Test
    fun `dockerignore should exist in docker directory`() {
        assertTrue(
            Files.isRegularFile(devopsDir.resolve("docker/.dockerignore")),
            ".dockerignore should exist at devops/docker/.dockerignore"
        )
    }

    // --- CI/CD files ---

    @Test
    fun `CI CD workflow file should exist`() {
        assertTrue(
            Files.isRegularFile(devopsDir.resolve("ci/ci-cd.yml")),
            "ci-cd.yml should exist at devops/ci/ci-cd.yml"
        )
    }

    // --- Best practices: separation of concerns ---

    @Test
    fun `devops directory should have separate concerns`() {
        val expectedConcerns = listOf("docker", "helm", "terraform", "ci", "scripts", "docs")
        val actualDirs = Files.list(devopsDir)
            .filter { Files.isDirectory(it) }
            .map { it.fileName.toString() }
            .toList()

        for (concern in expectedConcerns) {
            assertTrue(
                actualDirs.contains(concern),
                "devops/ should contain a '$concern' directory for separation of concerns"
            )
        }
    }

    @Test
    fun `terraform should have modules and environments separation`() {
        val terraformDir = devopsDir.resolve("terraform")
        assertTrue(Files.isDirectory(terraformDir.resolve("modules")), "terraform/modules/ should exist")
        assertTrue(Files.isDirectory(terraformDir.resolve("environments")), "terraform/environments/ should exist")
    }
}
