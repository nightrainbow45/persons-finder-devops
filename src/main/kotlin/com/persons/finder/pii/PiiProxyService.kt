package com.persons.finder.pii

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * HTTP proxy service that intercepts outbound LLM requests,
 * redacts PII before forwarding, and restores tokens in responses.
 * All external API calls are audit-logged with PII detection results.
 */
class PiiProxyService(
    private val config: RedactionConfig = RedactionConfig(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build(),
    private val auditLogger: AuditLogger = AuditLogger()
) {

    private val redactor = PiiRedactor(config)

    data class ProxyRequest(
        val url: String,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val method: String = "POST"
    )

    data class ProxyResponse(
        val statusCode: Int,
        val body: String,
        val piiDetected: List<PiiType>,
        val redactionsApplied: Int
    )

    /**
     * Processes an outbound request: detects and redacts PII, forwards the
     * sanitized request to the destination, then restores tokens in the response.
     */
    fun processRequest(request: ProxyRequest): ProxyResponse {
        // Redact PII from request body
        val redactionResult = redactor.redact(request.body)

        // Build and send the sanitized HTTP request
        val httpRequestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(request.url))
            .timeout(Duration.ofSeconds(60))

        request.headers.forEach { (key, value) ->
            httpRequestBuilder.header(key, value)
        }

        val httpRequest = when (request.method.uppercase()) {
            "POST" -> httpRequestBuilder.POST(HttpRequest.BodyPublishers.ofString(redactionResult.redactedText)).build()
            "PUT" -> httpRequestBuilder.PUT(HttpRequest.BodyPublishers.ofString(redactionResult.redactedText)).build()
            "GET" -> httpRequestBuilder.GET().build()
            else -> httpRequestBuilder.POST(HttpRequest.BodyPublishers.ofString(redactionResult.redactedText)).build()
        }

        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        // Restore tokens in the response body
        val restoredBody = redactor.restore(httpResponse.body(), redactionResult.tokenMap)

        // Audit log the external API call with PII detection results
        auditLogger.log(
            AuditLogEntry(
                requestId = UUID.randomUUID().toString(),
                piiDetected = redactionResult.detectedPiiTypes,
                redactionsApplied = redactionResult.tokenMap.size,
                destination = request.url,
                method = request.method.uppercase()
            )
        )

        return ProxyResponse(
            statusCode = httpResponse.statusCode(),
            body = restoredBody,
            piiDetected = redactionResult.detectedPiiTypes,
            redactionsApplied = redactionResult.tokenMap.size
        )
    }

    /**
     * Redacts PII from text without forwarding. Useful for testing or pre-processing.
     */
    fun redactOnly(text: String): PiiRedactor.RedactionResult {
        return redactor.redact(text)
    }
}
