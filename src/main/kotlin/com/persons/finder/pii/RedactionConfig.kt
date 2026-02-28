package com.persons.finder.pii

/**
 * Configuration for PII redaction rules and patterns.
 * Supports configurable detection patterns for person names and geographic coordinates.
 */
data class RedactionConfig(
    val rules: List<RedactionRule> = defaultRules(),
    val enabled: Boolean = true
) {
    companion object {
        fun defaultRules(): List<RedactionRule> = listOf(
            RedactionRule(
                type = PiiType.PERSON_NAME,
                // Matches capitalized words (first + last name patterns)
                pattern = "\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+\\b",
                action = RedactionAction.TOKENIZE
            ),
            RedactionRule(
                type = PiiType.COORDINATE,
                // Matches latitude/longitude pairs like: -12.345, 67.890 or (lat: -12.345, lon: 67.890)
                pattern = "-?\\d{1,3}\\.\\d{1,15}",
                action = RedactionAction.TOKENIZE
            )
        )
    }
}

data class RedactionRule(
    val type: PiiType,
    val pattern: String,
    val action: RedactionAction
)

enum class PiiType {
    PERSON_NAME,
    COORDINATE
}

enum class RedactionAction {
    REDACT,
    TOKENIZE
}
