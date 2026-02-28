package com.persons.finder.pii

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class AuditLoggerTest {

    private fun captureOutput(block: (AuditLogger) -> Unit): String {
        val baos = ByteArrayOutputStream()
        val logger = AuditLogger(PrintStream(baos))
        block(logger)
        return baos.toString().trim()
    }

    @Test
    fun `log entry is valid JSON with all required fields`() {
        val output = captureOutput { logger ->
            logger.log(
                AuditLogEntry(
                    timestamp = "2024-01-15T10:30:00Z",
                    requestId = "req-123",
                    piiDetected = listOf(PiiType.PERSON_NAME),
                    redactionsApplied = 2,
                    destination = "https://api.openai.com/v1/chat",
                    method = "POST"
                )
            )
        }

        assertTrue(output.contains("\"type\":\"PII_AUDIT\""))
        assertTrue(output.contains("\"timestamp\":\"2024-01-15T10:30:00Z\""))
        assertTrue(output.contains("\"requestId\":\"req-123\""))
        assertTrue(output.contains("\"piiDetected\":[\"PERSON_NAME\"]"))
        assertTrue(output.contains("\"redactionsApplied\":2"))
        assertTrue(output.contains("\"destination\":\"https://api.openai.com/v1/chat\""))
        assertTrue(output.contains("\"method\":\"POST\""))
    }

    @Test
    fun `log entry with no PII detected has empty array`() {
        val output = captureOutput { logger ->
            logger.log(
                AuditLogEntry(
                    requestId = "req-456",
                    piiDetected = emptyList(),
                    redactionsApplied = 0,
                    destination = "https://api.example.com",
                    method = "GET"
                )
            )
        }

        assertTrue(output.contains("\"piiDetected\":[]"))
        assertTrue(output.contains("\"redactionsApplied\":0"))
    }

    @Test
    fun `log entry with multiple PII types lists all`() {
        val output = captureOutput { logger ->
            logger.log(
                AuditLogEntry(
                    requestId = "req-789",
                    piiDetected = listOf(PiiType.PERSON_NAME, PiiType.COORDINATE),
                    redactionsApplied = 3,
                    destination = "https://api.openai.com/v1/chat",
                    method = "POST"
                )
            )
        }

        assertTrue(output.contains("\"PERSON_NAME\""))
        assertTrue(output.contains("\"COORDINATE\""))
    }

    @Test
    fun `log entry escapes special characters in destination`() {
        val output = captureOutput { logger ->
            logger.log(
                AuditLogEntry(
                    requestId = "req-esc",
                    piiDetected = emptyList(),
                    redactionsApplied = 0,
                    destination = "https://api.example.com/path?q=\"test\"",
                    method = "POST"
                )
            )
        }

        assertTrue(output.contains("\\\"test\\\""))
    }

    @Test
    fun `log entry is single line`() {
        val output = captureOutput { logger ->
            logger.log(
                AuditLogEntry(
                    requestId = "req-line",
                    piiDetected = listOf(PiiType.PERSON_NAME),
                    redactionsApplied = 1,
                    destination = "https://api.openai.com/v1/chat",
                    method = "POST"
                )
            )
        }

        assertEquals(1, output.lines().size)
    }
}
