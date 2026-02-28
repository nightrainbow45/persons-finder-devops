package com.persons.finder.pii

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for PII redaction service components.
 * Validates: Requirements 5.2, 5.3
 */
class PiiRedactionServiceTest {

    // --- PiiDetector Tests ---

    @Test
    fun `detector finds person name like John Smith`() {
        val detector = PiiDetector()
        // Use lowercase prefix so only the capitalized name matches
        val matches = detector.detect("hello John Smith, welcome!")
        val nameMatches = matches.filter { it.type == PiiType.PERSON_NAME }
        assertEquals(1, nameMatches.size)
        assertEquals("John Smith", nameMatches[0].value)
    }

    @Test
    fun `detector finds multi-word person name`() {
        val detector = PiiDetector()
        // Use lowercase prefix to avoid it being part of the name match
        val matches = detector.detect("contact Alice Bob Charlie for details")
        val nameMatches = matches.filter { it.type == PiiType.PERSON_NAME }
        assertEquals(1, nameMatches.size)
        assertEquals("Alice Bob Charlie", nameMatches[0].value)
    }

    @Test
    fun `detector finds valid coordinates`() {
        val detector = PiiDetector()
        val matches = detector.detect("Location is 37.7749 and -122.4194")
        val coordMatches = matches.filter { it.type == PiiType.COORDINATE }
        val values = coordMatches.map { it.value }.toSet()
        assertTrue(values.contains("37.7749"))
        assertTrue(values.contains("-122.4194"))
    }

    @Test
    fun `detector rejects coordinates outside valid range`() {
        val detector = PiiDetector()
        val matches = detector.detect("Invalid coord 200.12345")
        val coordMatches = matches.filter { it.type == PiiType.COORDINATE }
        assertTrue(coordMatches.isEmpty())
    }

    @Test
    fun `detector returns empty list for text without PII`() {
        val detector = PiiDetector()
        val matches = detector.detect("just some plain text with no pii")
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `detector returns empty list when config is disabled`() {
        val config = RedactionConfig(enabled = false)
        val detector = PiiDetector(config)
        val matches = detector.detect("hello John Smith at 37.7749")
        assertTrue(matches.isEmpty())
    }

    // --- PiiRedactor Tests ---

    @Test
    fun `redactor replaces person names with tokens`() {
        val redactor = PiiRedactor()
        val result = redactor.redact("hello John Smith")
        assertFalse(result.redactedText.contains("John Smith"))
        assertTrue(result.redactedText.contains("<NAME_"))
        assertTrue(result.tokenMap.values.contains("John Smith"))
        assertTrue(result.detectedPiiTypes.contains(PiiType.PERSON_NAME))
    }

    @Test
    fun `redactor replaces coordinates with tokens`() {
        val redactor = PiiRedactor()
        val result = redactor.redact("Lat 37.7749")
        assertFalse(result.redactedText.contains("37.7749"))
        assertTrue(result.redactedText.contains("<COORD_"))
        assertTrue(result.tokenMap.values.contains("37.7749"))
        assertTrue(result.detectedPiiTypes.contains(PiiType.COORDINATE))
    }

    @Test
    fun `restore reverses redaction`() {
        val redactor = PiiRedactor()
        val original = "hello John Smith at 37.7749"
        val result = redactor.redact(original)
        val restored = redactor.restore(result.redactedText, result.tokenMap)
        assertEquals(original, restored)
    }

    @Test
    fun `redactor returns unchanged text when no PII present`() {
        val redactor = PiiRedactor()
        val text = "just some plain text"
        val result = redactor.redact(text)
        assertEquals(text, result.redactedText)
        assertTrue(result.tokenMap.isEmpty())
        assertTrue(result.detectedPiiTypes.isEmpty())
    }

    @Test
    fun `redactor handles multiple PII instances`() {
        val redactor = PiiRedactor()
        val result = redactor.redact("met John Smith and Jane Doe at 37.7749 and -122.4194")
        assertFalse(result.redactedText.contains("John Smith"))
        assertFalse(result.redactedText.contains("Jane Doe"))
        assertFalse(result.redactedText.contains("37.7749"))
        assertFalse(result.redactedText.contains("-122.4194"))
        assertTrue(result.tokenMap.size >= 4)
        assertTrue(result.detectedPiiTypes.contains(PiiType.PERSON_NAME))
        assertTrue(result.detectedPiiTypes.contains(PiiType.COORDINATE))
    }
}
