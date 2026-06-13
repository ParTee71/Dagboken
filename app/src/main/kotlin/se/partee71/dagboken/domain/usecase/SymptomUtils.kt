package se.partee71.dagboken.domain.usecase

/**
 * Symptom wire format: "Name:Score,Name:Score"
 * Identical to the Expo app for backup compatibility.
 */
object SymptomUtils {

    fun encode(scores: Map<String, Int>): String =
        scores.entries
            .filter { it.value > 0 }
            .joinToString(",") { "${it.key}:${it.value}" }

    fun decode(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { part ->
                val idx = part.lastIndexOf(':')
                if (idx < 0) return@mapNotNull null
                val name  = part.substring(0, idx).trim()
                val score = part.substring(idx + 1).trim().toIntOrNull() ?: return@mapNotNull null
                name to score
            }
            .toMap()
    }

    fun sum(raw: String): Int = decode(raw).values.sum()
}
