package se.partee71.dagboken.core.model

/**
 * En loggad känsla på en post (SCR-7, DAT-14): tokens ur [FeelingCatalog] – kärnan, nyansen och det precisare ordet,
 * som i Willcox känslohjul. Tokens är engelska, i gemener och stabila (de lagras); de svenska namnen finns bara i
 * appens resurser (`feeling_<core>[_<nuance>[_<detail>]]`), aldrig i `:core`. [nuance] och [detail] är `null` när de
 * inte valts; ett [detail] utan [nuance] är inget känt ord ([FeelingCatalog.isKnown]) och lagras inte ([token]). Lagras
 * som texten [token], `core[.nuance[.detail]]`; ett okänt token med giltig form (från en nyare app) bevaras av codecen
 * och skrivs tillbaka oförändrat.
 */
data class Feeling(val core: String, val nuance: String? = null, val detail: String? = null) {
    /** Den lagrade formen `core[.nuance[.detail]]` – enda stället som skriver den; utan [nuance] skrivs ingen [detail]. */
    val token: String
        get() = buildString {
            append(core)
            nuance?.let { n ->
                append(SEPARATOR).append(n)
                detail?.let { append(SEPARATOR).append(it) }
            }
        }

    companion object {
        const val SEPARATOR = '.'

        /** Längsta token-del; samma gräns i rules och `DocumentRules.FEELING_TOKEN`. */
        const val MAX_PART = 32

        /** En token-del: 1–[MAX_PART] gemener a–z – det rules släpper igenom. */
        val PART = Regex("[a-z]{1,$MAX_PART}")

        /**
         * Enda stället som läser den lagrade formen: 1–3 delar runt [SEPARATOR], var och en enligt [PART]; allt annat →
         * `null`. Ett okänt ord med giltig form (nyare app) läses och skrivs tillbaka (en okänd kärna nekas ändå av rules
         * tills de höjts, som en okänd form); en del med fel tecken (versal, siffra, för lång) hoppas över och skrivs
         * aldrig tillbaka.
         */
        fun parse(text: String): Feeling? {
            val parts = text.split(SEPARATOR)
            if (parts.size !in 1..3 || parts.any { !PART.matches(it) }) return null
            return Feeling(parts[0], parts.getOrNull(1), parts.getOrNull(2))
        }
    }
}

/**
 * Ordkatalogen (SCR-8): sex kärnkänslor, var och en med sex nyanser som var och en har två precisare ord – 72 ord på
 * tre nivåer, 114 unika tokens.
 * Ordningen är hjulets ordning (medurs från klockan 12) och bevaras i [cores], [nuances] och [details].
 */
object FeelingCatalog {
    /** Högst så många känslor på en logg (`isFeelings` i rules, DAT-14). */
    const val MAX_FEELINGS = 6

    private class Nuance(val token: String, val details: List<String>)

    private class Core(val token: String, val nuances: List<Nuance>)

    private fun core(token: String, vararg nuances: Pair<String, Pair<String, String>>) = Core(token, nuances.map { (nuance, details) -> Nuance(nuance, listOf(details.first, details.second)) })

    private val wheel: List<Core> = listOf(
        core(
            "glad",
            "excited" to ("enthusiastic" to "giddy"),
            "energetic" to ("alert" to "lively"),
            "playful" to ("mischievous" to "joking"),
            "creative" to ("inspired" to "curious"),
            "hopeful" to ("optimistic" to "expectant"),
            "content" to ("satisfied" to "fulfilled")
        ),
        core(
            "strong",
            "secure" to ("safe" to "grounded"),
            "proud" to ("successful" to "worthwhile"),
            "brave" to ("determined" to "fearless"),
            "focused" to ("clear" to "purposeful"),
            "appreciated" to ("seen" to "affirmed"),
            "respected" to ("trusted" to "important")
        ),
        core(
            "calm",
            "grateful" to ("humble" to "moved"),
            "loving" to ("warm" to "tender"),
            "trusting" to ("open" to "reassured"),
            "relaxed" to ("serene" to "restful"),
            "present" to ("centered" to "aware"),
            "caring" to ("kind" to "generous")
        ),
        core(
            "sad",
            "lonely" to ("abandoned" to "isolated"),
            "hurt" to ("offended" to "devastated"),
            "guilty" to ("ashamed" to "remorseful"),
            "bored" to ("indifferent" to "restless"),
            "tired" to ("exhausted" to "empty"),
            "disappointed" to ("dejected" to "discouraged")
        ),
        core(
            "angry",
            "irritated" to ("impatient" to "touchy"),
            "frustrated" to ("blocked" to "resigned"),
            "critical" to ("skeptical" to "judgmental"),
            "distant" to ("cold" to "detached"),
            "jealous" to ("suspicious" to "bitter"),
            "hostile" to ("hateful" to "aggressive")
        ),
        core(
            "scared",
            "anxious" to ("overwhelmed" to "tense"),
            "insecure" to ("hesitant" to "shy"),
            "confused" to ("lost" to "scattered"),
            "helpless" to ("powerless" to "stuck"),
            "rejected" to ("excluded" to "unwanted"),
            "inferior" to ("inadequate" to "small")
        )
    )

    /** Kärnkänslorna i hjulets ordning – samma lista som kärnorna i `isFeelings` mönster i rules (`DocumentRules.FEELING_TOKEN`). */
    val cores: List<String> = wheel.map { it.token }

    /** Nyanserna till [core] i hjulets ordning; tom för en okänd kärna. */
    fun nuances(core: String): List<String> = coreOf(core)?.nuances?.map { it.token }.orEmpty()

    /** De precisare orden till [nuance] under [core]; tom när någon av dem är okänd. */
    fun details(core: String, nuance: String): List<String> = nuanceOf(core, nuance)?.details.orEmpty()

    /** Om varje satt token i [feeling] finns i katalogen på sin plats – ett [Feeling.detail] utan [Feeling.nuance] är okänt. */
    fun isKnown(feeling: Feeling): Boolean {
        val core = coreOf(feeling.core) ?: return false
        val nuance = feeling.nuance ?: return feeling.detail == null
        val known = core.nuances.firstOrNull { it.token == nuance } ?: return false
        return feeling.detail == null || feeling.detail in known.details
    }

    private fun coreOf(token: String) = wheel.firstOrNull { it.token == token }

    private fun nuanceOf(core: String, nuance: String) = coreOf(core)?.nuances?.firstOrNull { it.token == nuance }
}
