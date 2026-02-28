package com.persons.finder.pii

import java.io.PrintStream

/**
 * Writes PII redaction audit log entries to an output stream in JSON format.
 * Defaults to stdout for container-friendly structured logging.
 */
class AuditLogger(private val output: PrintStream = System.out) {

    /**
     * Logs an audit entry as a single-line JSON object.
     */
    fun log(entry: AuditLogEntry) {
        val piiList = entry.piiDetected.joinToString(",") { "\"${it.name}\"" }
        val json = buildString {
            append('{')
            append("\"type\":\"PII_AUDIT\",")
            append("\"timestamp\":\"${escape(entry.timestamp)}\",")
            append("\"requestId\":\"${escape(entry.requestId)}\",")
            append("\"piiDetected\":[${piiList}],")
            append("\"redactionsApplied\":${entry.redactionsApplied},")
            append("\"destination\":\"${escape(entry.destination)}\",")
            append("\"method\":\"${escape(entry.method)}\"")
            append('}')
        }
        output.println(json)
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
