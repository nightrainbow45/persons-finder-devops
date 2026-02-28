package com.persons.finder.pii

/**
 * Detects PII (Personally Identifiable Information) in text using regex patterns.
 * Supports detection of person names and geographic coordinates.
 */
class PiiDetector(private val config: RedactionConfig = RedactionConfig()) {

    data class PiiMatch(
        val value: String,
        val type: PiiType,
        val startIndex: Int,
        val endIndex: Int
    )

    /**
     * Detects all PII occurrences in the given text based on configured rules.
     * Returns matches sorted by start index in descending order (for safe replacement).
     */
    fun detect(text: String): List<PiiMatch> {
        if (!config.enabled) return emptyList()

        val matches = mutableListOf<PiiMatch>()

        for (rule in config.rules) {
            val regex = Regex(rule.pattern)
            for (match in regex.findAll(text)) {
                val candidate = match.value

                if (rule.type == PiiType.COORDINATE && !isValidCoordinate(candidate)) {
                    continue
                }

                matches.add(
                    PiiMatch(
                        value = candidate,
                        type = rule.type,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1
                    )
                )
            }
        }

        // Sort descending by start index for safe replacement from end to start
        return matches.sortedByDescending { it.startIndex }
    }

    private fun isValidCoordinate(value: String): Boolean {
        val num = value.toDoubleOrNull() ?: return false
        // Valid latitude: -90..90, valid longitude: -180..180
        // Accept the wider range to catch both lat and lon
        return num in -180.0..180.0
    }
}
