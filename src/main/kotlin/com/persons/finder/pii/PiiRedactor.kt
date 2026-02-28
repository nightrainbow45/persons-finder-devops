package com.persons.finder.pii

import java.util.UUID

/**
 * Redacts/tokenizes PII in text with reversible mapping.
 * Tokens can be used to restore original values in LLM responses.
 */
class PiiRedactor(private val config: RedactionConfig = RedactionConfig()) {

    private val detector = PiiDetector(config)

    data class RedactionResult(
        val redactedText: String,
        val tokenMap: Map<String, String>,
        val detectedPiiTypes: List<PiiType>
    )

    /**
     * Redacts all PII in the given text. Returns the redacted text and a token map
     * that can be used to reverse the redaction.
     */
    fun redact(text: String): RedactionResult {
        val matches = detector.detect(text)
        if (matches.isEmpty()) {
            return RedactionResult(text, emptyMap(), emptyList())
        }

        val tokenMap = mutableMapOf<String, String>()
        val piiTypes = mutableSetOf<PiiType>()
        var result = text

        // Matches are sorted descending by startIndex, so we can replace safely
        for (match in matches) {
            piiTypes.add(match.type)

            val existingToken = tokenMap.entries.find { it.value == match.value }?.key
            val token = existingToken ?: generateToken(match.type)

            if (existingToken == null) {
                tokenMap[token] = match.value
            }

            result = result.substring(0, match.startIndex) + token + result.substring(match.endIndex)
        }

        return RedactionResult(result, tokenMap, piiTypes.toList())
    }

    /**
     * Restores original PII values in text using the token map from a previous redaction.
     */
    fun restore(text: String, tokenMap: Map<String, String>): String {
        var result = text
        for ((token, original) in tokenMap) {
            result = result.replace(token, original)
        }
        return result
    }

    private fun generateToken(type: PiiType): String {
        val id = UUID.randomUUID().toString().substring(0, 8)
        return when (type) {
            PiiType.PERSON_NAME -> "<NAME_$id>"
            PiiType.COORDINATE -> "<COORD_$id>"
        }
    }
}
