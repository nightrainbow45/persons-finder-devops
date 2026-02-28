package com.persons.finder.pii

import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import org.junit.jupiter.api.Assertions.*

/**
 * Property-based test for PII Redaction Completeness.
 *
 * **Feature: devops-production-deployment, Property 1: PII Redaction Completeness**
 * **Validates: Requirements 5.3**
 *
 * Verifies that:
 * - Person names are fully redacted from output text
 * - Coordinates are fully redacted from output text
 * - Mixed PII text has all PII removed after redaction
 * - Redaction is reversible: redact then restore returns original text
 */
class PiiRedactionCompletenessPropertyTest {

    private val redactor = PiiRedactor()
    private val detector = PiiDetector()

    // --- Generators ---

    @Provide
    fun personNames(): Arbitrary<String> {
        val firstNames = Arbitraries.of(
            "John", "Jane", "Alice", "Bob", "Carlos", "Diana",
            "Edward", "Fiona", "George", "Hannah", "Ivan", "Julia",
            "Kevin", "Laura", "Michael", "Nancy", "Oscar", "Patricia"
        )
        val lastNames = Arbitraries.of(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia",
            "Miller", "Davis", "Rodriguez", "Martinez", "Anderson", "Taylor",
            "Thomas", "Jackson", "White", "Harris", "Martin", "Thompson"
        )
        return combine(firstNames, lastNames).`as` { first, last -> "$first $last" }
    }

    @Provide
    fun coordinates(): Arbitrary<String> {
        return Arbitraries.doubles()
            .between(-180.0, 180.0)
            .ofScale(6)
            .map { String.format("%.6f", it) }
    }

    @Provide
    fun textsWithPersonName(): Arbitrary<Pair<String, String>> {
        return personNames().map { name ->
            Pair("Please find information about $name in the database.", name)
        }
    }

    @Provide
    fun textsWithCoordinate(): Arbitrary<Pair<String, String>> {
        return coordinates().map { coord ->
            Pair("The location is at latitude $coord degrees.", coord)
        }
    }

    @Provide
    fun textsWithMixedPii(): Arbitrary<String> {
        val names = personNames()
        val lats = Arbitraries.doubles().between(-90.0, 90.0).ofScale(6)
            .map { String.format("%.6f", it) }
        val lons = Arbitraries.doubles().between(-180.0, 180.0).ofScale(6)
            .map { String.format("%.6f", it) }

        return combine(names, lats, lons).`as` { name, lat, lon ->
            "User $name is located at coordinates $lat, $lon in the system."
        }
    }

    // --- Property Tests ---

    /**
     * Property: Person names are fully redacted from output text.
     * For any generated person name embedded in text, the redacted output
     * must not contain the original name.
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    fun `person names are fully redacted from output`(
        @ForAll("textsWithPersonName") input: Pair<String, String>
    ): Boolean {
        val (text, name) = input
        val result = redactor.redact(text)
        return !result.redactedText.contains(name)
    }

    /**
     * Property: Coordinates are fully redacted from output text.
     * For any generated coordinate value embedded in text, the redacted output
     * must not contain the original coordinate.
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    fun `coordinates are fully redacted from output`(
        @ForAll("textsWithCoordinate") input: Pair<String, String>
    ): Boolean {
        val (text, coord) = input
        val result = redactor.redact(text)
        return !result.redactedText.contains(coord)
    }

    /**
     * Property: Mixed PII text has all detected PII removed after redaction.
     * For any text containing both person names and coordinates, the redacted
     * output must not contain any originally detected PII values.
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    fun `all PII is removed from redacted output in mixed text`(
        @ForAll("textsWithMixedPii") text: String
    ): Boolean {
        val detectedBefore = detector.detect(text)
        val result = redactor.redact(text)

        // None of the originally detected PII values should appear in redacted text
        return detectedBefore.all { match ->
            !result.redactedText.contains(match.value)
        }
    }

    /**
     * Property: Redaction is reversible — redact then restore returns original text.
     * For any text with PII, applying redact followed by restore with the token map
     * must produce the original text.
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    fun `redaction is reversible via restore`(
        @ForAll("textsWithMixedPii") text: String
    ): Boolean {
        val result = redactor.redact(text)
        val restored = redactor.restore(result.redactedText, result.tokenMap)
        return restored == text
    }

    /**
     * Property: Detected PII types are correctly reported in the redaction result.
     * When text contains person names and coordinates, the result must report
     * both PII types as detected.
     *
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    fun `redaction result reports all detected PII types`(
        @ForAll("textsWithMixedPii") text: String
    ): Boolean {
        val result = redactor.redact(text)
        // Mixed text always has a name and coordinates
        return result.detectedPiiTypes.contains(PiiType.PERSON_NAME) &&
            result.detectedPiiTypes.contains(PiiType.COORDINATE)
    }
}
