package com.persons.finder.pii

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditLogEntryTest {

    @Test
    fun `entry has all required fields`() {
        val entry = AuditLogEntry(
            timestamp = "2024-01-15T10:30:00Z",
            requestId = "req-123",
            piiDetected = listOf(PiiType.PERSON_NAME),
            redactionsApplied = 2,
            destination = "https://api.openai.com/v1/chat",
            method = "POST"
        )

        assertEquals("2024-01-15T10:30:00Z", entry.timestamp)
        assertEquals("req-123", entry.requestId)
        assertEquals(listOf(PiiType.PERSON_NAME), entry.piiDetected)
        assertEquals(2, entry.redactionsApplied)
        assertEquals("https://api.openai.com/v1/chat", entry.destination)
        assertEquals("POST", entry.method)
    }

    @Test
    fun `entry generates timestamp by default`() {
        val entry = AuditLogEntry(
            requestId = "req-auto",
            piiDetected = emptyList(),
            redactionsApplied = 0,
            destination = "https://api.example.com",
            method = "GET"
        )

        assertTrue(entry.timestamp.isNotEmpty())
    }
}
