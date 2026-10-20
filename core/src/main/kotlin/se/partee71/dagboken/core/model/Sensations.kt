package se.partee71.dagboken.core.model

/**
 * En katalog med valbara ord som lagras som **tokens** – engelska, gemener, stabila (de lagras) och på formen [TOKEN] –
 * för måendeloggens steg "Kropp och sammanhang" (DAT-15): [BodySensationCatalog] och [ContextTagCatalog]. Ordningen i
 * [tokens] är visningsordningen. De svenska namnen finns bara i appens resurser, med nyckeln `<prefix>_<token>`
 * (`body_tense_jaw`, `context_alone_time`), aldrig i `:core`. Ett okänt token med giltig form (från en nyare app) bevaras av
 * codecen och skrivs tillbaka oförändrat; rules prövar bara formen ([TOKEN]), inte katalogtillhörigheten.
 *
 * [exclusive] är ett token som utesluter alla andra (för kroppen "Ingenting särskilt"); `null` = vanlig växling.
 */
open class TagCatalog(val tokens: List<String>, val exclusive: String? = null) {
    init {
        require(tokens.all(TOKEN::matches) && tokens.toSet().size == tokens.size && tokens.size <= MAX_TAGS) { "ogiltig katalog" }
        require(exclusive == null || exclusive in tokens) { "det uteslutande tokenet saknas i katalogen" }
    }

    /**
     * [selected] med [token] växlat – enda stället för regeln (DAT-15). Ett valt token avmarkeras; väljs [exclusive]
     * avmarkeras alla andra, och väljs något annat avmarkeras [exclusive]. Resultatet följer katalogens ordning, med
     * okända tokens (från en nyare app) sist i sin ordning; dubbletter slås ihop. Ett nytt val som skulle ge fler än
     * [MAX_TAGS] (bara möjligt med okända tokens) görs inte – då kommer [selected] tillbaka oförändrad.
     */
    fun toggle(selected: List<String>, token: String): List<String> {
        val current = selected.distinct()
        if (token in current) return ordered(current - token)
        val next = when (token) {
            exclusive -> listOf(token)
            else -> current.filter { it != exclusive } + token
        }
        return if (next.size > MAX_TAGS) selected else ordered(next)
    }

    /** [selected] i katalogens ordning; okända tokens sist, i den ordning de stod. */
    fun ordered(selected: List<String>): List<String> = selected.sortedBy { tokens.indexOf(it).takeIf { i -> i >= 0 } ?: tokens.size }

    companion object {
        /** Högst så många tokens i `body` och i `context` (`isTags` i rules, DAT-15) – antalet ord i den största katalogen. */
        const val MAX_TAGS = 11

        /** Längsta token; samma gräns i rules och `DocumentRules.TAG_TOKEN`. */
        const val MAX_TOKEN = 32

        /**
         * Orden som ett element av annan typ blir i rules `join(' ')` – sant/falskt och `null` – och som därför inte är
         * tokens: att [TOKEN] utesluter dem gör att rules nekar sådana element med samma mönster, utan en typkontroll per
         * element (som inte rymdes i måendeloggens uttrycksbudget, #321).
         */
        val RESERVED: List<String> = listOf("true", "false", "null")

        private const val C = "[a-z_]"

        /**
         * Ett token: 1–[MAX_TOKEN] gemener a–z och `_`, utom [RESERVED] – exakt det rules släpper igenom (`isTags`); annan
         * form hoppas codecen över. RE2 (rules) saknar lookahead, så uteslutningen är utskriven: längd 1–3 eller 6–[MAX_TOKEN],
         * eller 4–5 tecken som någonstans avviker från `true`/`null` respektive `false`. SensationsTest prövar den mot
         * definitionen för alla korta strängar.
         */
        val TOKEN = Regex(
            "($C{1,3}|$C{6,$MAX_TOKEN}" +
                "|[a-mo-su-z_]$C{3}|t([a-qs-z_]$C{2}|r([a-tv-z_]$C|u[a-df-z_]))|n([a-tv-z_]$C{2}|u([a-km-z_]$C|l[a-km-z_]))" +
                "|[a-eg-z_]$C{4}|f([b-z_]$C{3}|a([a-km-z_]$C{2}|l([a-rt-z_]$C|s[a-df-z_]))))"
        )
    }
}

/**
 * Kroppskänslorna på en måendelogg (`screenings.body`, DAT-15): "Var märks det i kroppen?". Resursnyckeln är
 * `body_<token>`. [NOTHING] ("Ingenting särskilt") utesluter de andra ([TagCatalog.toggle]).
 */
object BodySensationCatalog : TagCatalog(
    listOf(
        "tense_jaw", // Spänd käke
        "heart_racing", // Hjärtklappning
        "stomach_knot", // Klump i magen
        "heavy_chest", // Tung i bröstet
        "restless_legs", // Rastlösa ben
        "warm_face", // Varm i ansiktet
        "headache", // Huvudvärk
        "tired_body", // Trött i kroppen
        "light_body", // Lätt i kroppen
        "warm_soft", // Varm och mjuk
        NOTHING_TOKEN // Ingenting särskilt
    ),
    exclusive = NOTHING_TOKEN
) {
    /** "Ingenting särskilt" – utesluter de andra. */
    const val NOTHING = NOTHING_TOKEN
}

/** Tokenet för "Ingenting särskilt" – skrivet en gång; [BodySensationCatalog.NOTHING] är namnet utåt. */
private const val NOTHING_TOKEN = "nothing"

/** Sammanhanget kring en måendelogg (`screenings.context`, DAT-15): vad det har med att göra. Resursnyckeln är `context_<token>`. */
object ContextTagCatalog : TagCatalog(
    listOf(
        "sleep", // Sömn
        "family", // Familj
        "work", // Arbete
        "health", // Hälsa
        "medicines", // Mediciner
        "alone_time", // Ensamtid
        "weather", // Väder
        "finances", // Ekonomi
        "friends", // Vänner
        "exercise", // Motion
        "food" // Mat
    )
)
