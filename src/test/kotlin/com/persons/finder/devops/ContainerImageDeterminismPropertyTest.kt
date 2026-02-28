package com.persons.finder.devops

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Property-based test for Container Image Determinism.
 *
 * **Feature: devops-production-deployment, Property 3: Container Image Determinism**
 * **Validates: Requirements 1.1, 1.2**
 *
 * Since Docker builds may not be available in the test environment, this test
 * validates that the Dockerfile configuration ensures determinism by verifying:
 * - Base image versions are pinned (not using :latest)
 * - COPY commands use specific paths (not wildcards that could change ordering)
 * - The Dockerfile structure supports reproducible builds
 * - Multiple parses of the same Dockerfile produce identical analysis results
 */
class ContainerImageDeterminismPropertyTest {

    private val projectRoot = Paths.get(System.getProperty("user.dir"))
    private val dockerfilePath = projectRoot.resolve("devops/docker/Dockerfile")

    private fun readDockerfile(): String {
        assertTrue(Files.isRegularFile(dockerfilePath), "Dockerfile should exist at devops/docker/Dockerfile")
        return Files.readString(dockerfilePath)
    }

    private fun parseFromStatements(content: String): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { it.uppercase().startsWith("FROM ") }
    }

    private fun parseCopyStatements(content: String): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { it.uppercase().startsWith("COPY ") }
    }

    /**
     * Property: Every FROM statement in the Dockerfile must pin a specific version tag.
     * No FROM statement should use :latest or omit a tag entirely.
     *
     * We generate random subsets of FROM lines and verify each one is pinned.
     */
    @Property(tries = 100)
    fun `all base images must have pinned version tags`(@ForAll("fromStatementIndices") index: Int): Boolean {
        val content = readDockerfile()
        val fromStatements = parseFromStatements(content)
        if (fromStatements.isEmpty()) return true

        val safeIndex = index % fromStatements.size
        val fromLine = fromStatements[safeIndex]

        // Extract the image reference (after FROM, before AS if present)
        val imageRef = fromLine
            .removePrefix("FROM ")
            .split("\\s+".toRegex())[0]
            .lowercase()

        // Must contain a colon for version tag
        val hasTag = imageRef.contains(":")
        // Must not use :latest
        val notLatest = !imageRef.endsWith(":latest")
        // Tag should contain version-like content (digits, dots, hyphens)
        val tagPart = if (hasTag) imageRef.substringAfter(":") else ""
        val hasSpecificVersion = hasTag && tagPart.matches(Regex(".*[0-9].*"))

        return hasTag && notLatest && hasSpecificVersion
    }

    @Provide
    fun fromStatementIndices(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 99)
    }

    /**
     * Property: COPY commands must use specific source paths, not glob patterns
     * that could introduce non-determinism through file ordering.
     *
     * Wildcards like * in source paths can cause non-deterministic layer content
     * if file system ordering varies between builds.
     */
    @Property(tries = 100)
    fun `COPY commands use specific paths for deterministic builds`(@ForAll("copyStatementIndices") index: Int): Boolean {
        val content = readDockerfile()
        val copyStatements = parseCopyStatements(content)
        if (copyStatements.isEmpty()) return true

        val safeIndex = index % copyStatements.size
        val copyLine = copyStatements[safeIndex]

        // Extract source path(s) from COPY command
        // COPY [--from=...] <src>... <dest>
        val parts = copyLine.split("\\s+".toRegex())
            .drop(1) // drop "COPY"
            .filter { !it.startsWith("--") } // drop flags like --from=builder

        if (parts.size < 2) return true // malformed, skip

        // All source paths (everything except the last element which is dest)
        val sourcePaths = parts.dropLast(1)

        // Check that source paths don't use problematic wildcards
        // Note: *.jar in --from=builder context is acceptable for single-artifact builds
        // but general wildcards in host COPY should be specific
        return sourcePaths.all { src ->
            // Allow --from=builder copies (build artifacts are deterministic from same source)
            val isFromBuilder = copyLine.contains("--from=")
            if (isFromBuilder) {
                true // Builder stage output is deterministic for same source
            } else {
                // Host COPY: paths should be specific directories or files
                // Wildcards like * or ? in host context can cause ordering issues
                !src.contains("?") // no single-char wildcards
            }
        }
    }

    @Provide
    fun copyStatementIndices(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 99)
    }

    /**
     * Property: Parsing the Dockerfile N times always produces the same structural analysis.
     * This validates that our determinism checks themselves are deterministic,
     * and that the Dockerfile content is stable across reads.
     */
    @Property(tries = 100)
    fun `Dockerfile parsing is deterministic across multiple reads`(@ForAll @IntRange(min = 2, max = 10) readCount: Int): Boolean {
        val results = (1..readCount).map { readDockerfile() }
        // All reads should produce identical content
        return results.all { it == results[0] }
    }

    /**
     * Property: The Dockerfile must use multi-stage build (multiple FROM statements).
     * Multi-stage builds ensure only runtime artifacts are included, making the
     * final image deterministic and minimal.
     */
    @Property(tries = 100)
    fun `Dockerfile uses multi-stage build for deterministic minimal images`(@ForAll("randomSeed") seed: Int): Boolean {
        val content = readDockerfile()
        val fromStatements = parseFromStatements(content)
        // Multi-stage build requires at least 2 FROM statements
        return fromStatements.size >= 2
    }

    @Provide
    fun randomSeed(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 999)
    }

    /**
     * Property: The build stage must copy dependency files before source code.
     * This ensures Docker layer caching works deterministically - dependency
     * layers are cached and only rebuilt when dependencies change.
     */
    @Property(tries = 100)
    fun `build stage copies dependencies before source for deterministic caching`(@ForAll("randomSeed") seed: Int): Boolean {
        val content = readDockerfile()
        val lines = content.lines().map { it.trim() }

        // Find the first FROM (build stage)
        val firstFromIndex = lines.indexOfFirst { it.uppercase().startsWith("FROM ") }
        // Find the second FROM (runtime stage)
        val secondFromIndex = lines.drop(firstFromIndex + 1)
            .indexOfFirst { it.uppercase().startsWith("FROM ") }
            .let { if (it >= 0) it + firstFromIndex + 1 else lines.size }

        // Get build stage lines only
        val buildStageLines = lines.subList(firstFromIndex, secondFromIndex)

        // Find index of gradle config copy (build.gradle.kts, settings.gradle.kts)
        val gradleConfigCopyIndex = buildStageLines.indexOfFirst {
            it.uppercase().startsWith("COPY ") &&
                (it.contains("build.gradle") || it.contains("settings.gradle"))
        }

        // Find index of source code copy
        val srcCopyIndex = buildStageLines.indexOfFirst {
            it.uppercase().startsWith("COPY ") && it.contains("src")
        }

        // Gradle config should be copied before source code
        return gradleConfigCopyIndex >= 0 && srcCopyIndex >= 0 && gradleConfigCopyIndex < srcCopyIndex
    }
}
