package com.persons.finder.pii

import java.time.Instant

/**
 * Data model for PII redaction audit log entries.
 * Each entry records metadata about an external API call and its PII detection results.
 */
data class AuditLogEntry(
    val timestamp: String = Instant.now().toString(),
    val requestId: String,
    val piiDetected: List<PiiType>,
    val redactionsApplied: Int,
    val destination: String,
    val method: String
)
