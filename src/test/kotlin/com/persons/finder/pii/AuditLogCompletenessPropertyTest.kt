package com.persons.finder.pii

import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant

/**
 * Property-based test for Audit Log Completeness.
 *
 * **Feature: devops-production-deployment, Property 2: Audit Log Completeness**
 * **Validates: Requirements 5.5**
 *
 * Verifies that:
 * - Every audit log entry produces valid JSON output
 * - Each logged entry produces exactly one log line
 * - All required fields are present in every log entry
 * - Chronological ordering of timestamps is preserved in log output
 */
class AuditLogCompletenessPropertyTest {

    // --- Generators ---

    @Provide
    fun requestIds(): Arbitrary<String> {
        val prefixes = Arbitraries.of("req", "call", "api", "rpc")
        val ids = Arbitraries.integers().between(1, 999999)
        return combine(prefixes, ids).`as` { prefix, id -> "$prefix-$id" }
    }

    @Provide
    fun piiTypeLists(): Arbitrary<List<PiiType>> {
        return Arbitraries.of(*PiiType.values())
            .list()
            .ofMinSize(0)
            .ofMaxSize(PiiType.values().size)
    }

    @Provide
    fun redactionCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 50)
    }

    @Provide
    fun destinations(): Arbitrary<String> {
        return Arbitraries.of(
            "https://api.openai.com/v1/chat/completions",
            "https://api.openai.com/v1/embeddings",
            "https://api.anthropic.com/v1/messages",
            "https://generativelanguage.googleapis.com/v1/models",
            "https://api.cohere.ai/v1/generate",
            "https://llm.internal.example.com/predict"
        )
    }

    @Provide
    fun httpMethods(): Arbitrary<String> {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH")
    }

    @Provide
    fun auditLogEntries(): Arbitrary<AuditLogEntry> {
        return combine(
            requestIds(),
            piiTypeLists(),
            redactionCounts(),
            destinations(),
            httpMethods()
        ).`as` { reqId, piiTypes, redactions, dest, method ->
            AuditLogEntry(
                timestamp = Instant.now().toString(),
                requestId = reqId,
                piiDetected = piiTypes,
                redactionsApplied = redactions,
                destination = dest,
                method = method
            )
        }
    }

    @Provide
    fun entrySequenceSizes(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 20)
    }

    // --- Helper ---

    private fun captureOutput(block: (AuditLogger) -> Unit): String {
        val baos = ByteArrayOutputStream()
        val logger = AuditLogger(PrintStream(baos))
        block(logger)
        return baos.toString()
    }

    // --- Property Tests ---

    /**
     * Property: Every audit log entry produces valid JSON output.
     * For any randomly generated AuditLogEntry, the logged output must be
     * parseable JSON (starts with '{', ends with '}', and contains balanced braces).
     *
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    fun `every audit log entry produces valid JSON`(
        @ForAll("auditLogEntries") entry: AuditLogEntry
    ): Boolean {
        val output = captureOutput { logger -> logger.log(entry) }.trim()

        // Must start with { and end with }
        if (!output.startsWith("{") || !output.endsWith("}")) return false

        // Balanced braces
        var depth = 0
        for (ch in output) {
            if (ch == '{') depth++
            if (ch == '}') depth--
            if (depth < 0) return false
        }
        if (depth != 0) return false

        // Balanced brackets (for arrays)
        var bracketDepth = 0
        for (ch in output) {
            if (ch == '[') bracketDepth++
            if (ch == ']') bracketDepth--
            if (bracketDepth < 0) return false
        }
        return bracketDepth == 0
    }

    /**
     * Property: Each logged entry produces exactly one log line.
     * For any randomly generated AuditLogEntry, calling log() must produce
     * exactly one non-empty line of output.
     *
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    fun `each logged entry produces exactly one line`(
        @ForAll("auditLogEntries") entry: AuditLogEntry
    ): Boolean {
        val output = captureOutput { logger -> logger.log(entry) }
        val lines = output.lines().filter { it.isNotBlank() }
        return lines.size == 1
    }

    /**
     * Property: All required fields are present in every log entry.
     * For any randomly generated AuditLogEntry, the JSON output must contain
     * the fields: type, timestamp, requestId, piiDetected, redactionsApplied,
     * destination, method.
     *
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    fun `all required fields are present in every log entry`(
        @ForAll("auditLogEntries") entry: AuditLogEntry
    ): Boolean {
        val output = captureOutput { logger -> logger.log(entry) }.trim()

        val requiredFields = listOf(
            "\"type\":",
            "\"timestamp\":",
            "\"requestId\":",
            "\"piiDetected\":",
            "\"redactionsApplied\":",
            "\"destination\":",
            "\"method\":"
        )

        return requiredFields.all { field -> output.contains(field) }
    }

    /**
     * Property: A sequence of entries each produces a corresponding log line.
     * For any random sequence of N audit log entries, logging all of them must
     * produce exactly N non-empty lines.
     *
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    fun `sequence of entries produces one log line per entry`(
        @ForAll("entrySequenceSizes") count: Int
    ): Boolean {
        val entries = (1..count).map { i ->
            AuditLogEntry(
                timestamp = Instant.now().toString(),
                requestId = "req-$i",
                piiDetected = if (i % 2 == 0) listOf(PiiType.PERSON_NAME) else emptyList(),
                redactionsApplied = i,
                destination = "https://api.openai.com/v1/chat",
                method = "POST"
            )
        }

        val output = captureOutput { logger ->
            entries.forEach { logger.log(it) }
        }

        val lines = output.lines().filter { it.isNotBlank() }
        return lines.size == count
    }

    /**
     * Property: Chronological ordering of timestamps is preserved in log output.
     * For any sequence of entries with increasing timestamps, the logged output
     * must preserve that chronological order.
     *
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 100)
    fun `chronological ordering of timestamps is preserved`(
        @ForAll("entrySequenceSizes") count: Int
    ): Boolean {
        val baseEpochSecond = 1700000000L
        val entries = (0 until count).map { i ->
            AuditLogEntry(
                timestamp = Instant.ofEpochSecond(baseEpochSecond + i * 60).toString(),
                requestId = "req-$i",
                piiDetected = listOf(PiiType.PERSON_NAME),
                redactionsApplied = 1,
                destination = "https://api.openai.com/v1/chat",
                method = "POST"
            )
        }

        val output = captureOutput { logger ->
            entries.forEach { logger.log(it) }
        }

        val lines = output.lines().filter { it.isNotBlank() }
        if (lines.size != count) return false

        // Extract timestamps from each line and verify ordering
        val timestampRegex = """"timestamp":"([^"]+)"""".toRegex()
        val timestamps = lines.mapNotNull { line ->
            timestampRegex.find(line)?.groupValues?.get(1)
        }

        if (timestamps.size != count) return false

        // Verify timestamps are in non-decreasing order
        return timestamps.zipWithNext().all { (a, b) ->
            Instant.parse(a) <= Instant.parse(b)
        }
    }
}
