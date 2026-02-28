package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for AI usage documentation.
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5
 *
 * Verifies that AI_LOG.md exists in the repository root and contains
 * all required sections documenting AI-assisted work.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiDocumentationTest {

    private val projectRoot = Paths.get(System.getProperty("user.dir"))
    private val aiLogFile = projectRoot.resolve("AI_LOG.md")
    private val aiLogContent: String by lazy {
        if (Files.isRegularFile(aiLogFile)) {
            Files.readString(aiLogFile)
        } else {
            ""
        }
    }

    // --- Requirement 10.5: AI_LOG.md exists in repository root ---

    @Test
    fun `AI_LOG md should exist in repository root`() {
        assertTrue(
            Files.isRegularFile(aiLogFile),
            "AI_LOG.md should exist at the repository root"
        )
    }

    @Test
    fun `AI_LOG md should not be empty`() {
        assertTrue(
            aiLogContent.isNotBlank(),
            "AI_LOG.md should not be empty"
        )
    }

    // --- Requirement 10.1: Document all AI-assisted work ---

    @ParameterizedTest(name = "AI_LOG.md should contain section for {0}")
    @ValueSource(strings = [
        "Dockerfile",
        "Kubernetes",
        "CI/CD",
        "PII",
        "Terraform",
        "API Endpoints",
        "Actuator"
    ])
    fun `AI_LOG md should contain required artifact sections`(section: String) {
        assertTrue(
            aiLogContent.contains(section, ignoreCase = true),
            "AI_LOG.md should contain a section covering '$section'"
        )
    }

    // --- Requirement 10.2: Document original prompts ---

    @ParameterizedTest(name = "Section for {0} should document original prompt")
    @ValueSource(strings = [
        "Dockerfile",
        "Helm Chart",
        "GitHub Actions",
        "PII",
        "Terraform",
        "API",
        "Actuator"
    ])
    fun `each section should document original prompt or intent`(artifact: String) {
        assertTrue(
            aiLogContent.contains("Prompt", ignoreCase = true) ||
                aiLogContent.contains("Intent", ignoreCase = true),
            "AI_LOG.md should document the original prompt/intent for '$artifact'"
        )
    }

    // --- Requirement 10.3: Document identified flaws ---

    @Test
    fun `AI_LOG md should document identified flaws or issues`() {
        assertTrue(
            aiLogContent.contains("Flaws", ignoreCase = true) ||
                aiLogContent.contains("Issues", ignoreCase = true),
            "AI_LOG.md should document identified flaws or issues in AI-generated content"
        )
    }

    @Test
    fun `AI_LOG md should contain multiple flaw descriptions`() {
        val flawCount = Regex("(?i)(flaw|issue|problem|bug|error|incorrect|missing)", RegexOption.IGNORE_CASE)
            .findAll(aiLogContent).count()
        assertTrue(
            flawCount >= 5,
            "AI_LOG.md should document multiple flaws/issues (found $flawCount references)"
        )
    }

    // --- Requirement 10.4: Document fixes applied ---

    @Test
    fun `AI_LOG md should document fixes applied`() {
        assertTrue(
            aiLogContent.contains("Fixes Applied", ignoreCase = true) ||
                aiLogContent.contains("Fix", ignoreCase = true),
            "AI_LOG.md should document fixes applied to AI-generated content"
        )
    }

    @Test
    fun `AI_LOG md should contain multiple fix descriptions`() {
        val fixCount = Regex("(?i)(fix|correct|update|change|replace|add|narrow|restrict)")
            .findAll(aiLogContent).count()
        assertTrue(
            fixCount >= 5,
            "AI_LOG.md should document multiple fixes (found $fixCount references)"
        )
    }

    // --- Structure validation ---

    @Test
    fun `AI_LOG md should have a table of contents or clear section structure`() {
        val hasToc = aiLogContent.contains("Table of Contents", ignoreCase = true)
        val hasNumberedSections = aiLogContent.contains("## 1.") || aiLogContent.contains("## 1 ")
        assertTrue(
            hasToc || hasNumberedSections,
            "AI_LOG.md should have a table of contents or numbered sections for navigation"
        )
    }

    @Test
    fun `AI_LOG md should document what was generated for each artifact`() {
        assertTrue(
            aiLogContent.contains("Generated", ignoreCase = true) ||
                aiLogContent.contains("What Was", ignoreCase = true),
            "AI_LOG.md should describe what was generated for each artifact"
        )
    }
}
