package se.partee71.dagboken.core.medicine

import java.text.Normalizer
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.medicineTitle

// Läkemedelsverkets lista över läkemedel som säljs i Sverige (REC-1, FAV-1) – port av ReseApotekets
// `core/medicine/MedicineCatalog.kt` utan Regimen-delarna. Helt lokalt: inga nätverksanrop, inget loggas.

/**
 * Ett läkemedel i listan, så som appen behöver det: "Alvedon" · "500 mg" · "Filmdragerad tablett"
 * ([formText]), som receptets [form].
 */
data class MedicineEntry(val name: String, val strength: String, val formText: String) {
    val form: MedicineForm get() = MedicineForms.of(formText)

    /** "Alvedon 500 mg" – det förslaget visar och sökningen matchar mot; samma regel som recepts `displayName`. */
    val title: String get() = medicineTitle(name, strength)
}

/** En träff och var i [MedicineEntry.title] den matchar – för markeringen i förslaget. */
data class MedicineMatch(val entry: MedicineEntry, val start: Int, val length: Int)

/**
 * Det inbyggda utdraget ur Läkemedelsverkets lista: sök på namn medan man skriver. Filen läses av [parse].
 */
class MedicineCatalog(val updated: LocalDate?, val entries: List<MedicineEntry>) {

    private val keys = entries.map { fold(it.title) }

    /**
     * Högst [limit] läkemedel vars namn med styrka ([MedicineEntry.title]) eller något ord i det
     * börjar med [query] – "levaxin 50" hittar Levaxin 50 mikrogram – utan hänsyn till skiftläge och
     * diakritiska tecken. Namnets början först, sedan ordstart; annars filens
     * ordning (svensk namnordning, minst styrka först). Under [MIN_QUERY] tecken ger inget.
     */
    fun search(query: String, limit: Int = LIMIT): List<MedicineMatch> {
        val needle = fold(query.trim())
        if (needle.length < MIN_QUERY) return emptyList()
        val first = mutableListOf<MedicineMatch>()
        val other = mutableListOf<MedicineMatch>()
        for (i in entries.indices) {
            val at = wordStart(keys[i], needle) ?: continue
            (if (at == 0) first else other) += MedicineMatch(entries[i], at, needle.length)
            if (first.size >= limit) break
        }
        return (first + other).take(limit)
    }

    companion object {
        const val MIN_QUERY = 3
        const val LIMIT = 5
        private const val HEADER = "#"
        private const val UPDATED = "# updated "

        /**
         * Filformatet: en rad per läkemedel, tabbseparerat `namn  styrka  form`; rader som börjar
         * med `#` är kommentarer (`# updated 2026-10-04`). Rader utan namn eller form hoppas över, och
         * fler kolumner än tre ignoreras – en äldre app klarar en nyare fil.
         */
        fun parse(text: String): MedicineCatalog {
            var updated: LocalDate? = null
            val entries = text.lineSequence().mapNotNull { line ->
                if (line.startsWith(UPDATED)) updated = runCatching { LocalDate.parse(line.removePrefix(UPDATED).trim()) }.getOrNull()
                if (line.isBlank() || line.startsWith(HEADER)) return@mapNotNull null
                val cols = line.split('\t')
                if (cols.size < 3 || cols[0].isBlank() || cols[2].isBlank()) return@mapNotNull null
                MedicineEntry(name = cols[0].trim(), strength = cols[1].trim(), formText = cols[2].trim())
            }.toList()
            return MedicineCatalog(updated, entries)
        }

        /** Gemener utan diakritiska tecken, tecken för tecken – så att positionerna stämmer med originalet. */
        internal fun fold(text: String): String = buildString(text.length) {
            text.forEach { c -> append(Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first().lowercaseChar()) }
        }

        private fun wordStart(haystack: String, needle: String): Int? {
            var from = 0
            while (true) {
                val at = haystack.indexOf(needle, from)
                if (at < 0) return null
                if (at == 0 || !haystack[at - 1].isLetterOrDigit()) return at
                from = at + 1
            }
        }
    }
}

/**
 * Enheten en medicin med formen räknas i när den fylls i från listan – formuläret byter annars aldrig
 * enhet vid formbyte. Text, som receptets och dosens `unit`.
 */
val MedicineForm.defaultUnit: String
    get() = when (this) {
        MedicineForm.TABLET -> "tablett"
        MedicineForm.CAPSULE -> "kapsel"
        MedicineForm.LIQUID, MedicineForm.DROPS -> "ml"
        MedicineForm.POWDER -> "dos"
        MedicineForm.INHALER -> "puff"
        MedicineForm.PATCH, MedicineForm.OTHER -> "st"
    }

/**
 * Vad ett val i listan fyller i, i receptformuläret och vid behov-formuläret: namn, styrka, form och
 * formens enhet. Dosen (antalet) och allt annat lämnas som det var.
 */
data class MedicineFill(val name: String, val strength: String, val form: MedicineForm, val unit: String)

/** Det [MedicineFill] ett val av läkemedlet ger. */
val MedicineEntry.fill: MedicineFill get() = MedicineFill(name, strength, form, form.defaultUnit)

/**
 * Receptet ifyllt från listan ([MedicineFill]); doshöjningarnas enhet följer receptets, som när enheten
 * byts i formuläret (REC-9). En okänd lagrad form ersätts av den valda.
 */
fun Prescription.filledFrom(entry: MedicineEntry): Prescription = entry.fill.let { fill ->
    copy(
        name = fill.name,
        strength = fill.strength,
        form = fill.form,
        unknownForm = null,
        unit = fill.unit,
        boosts = boosts.map { it.copy(unit = fill.unit) },
    )
}

/** Vid behov-medicinen ifylld från listan ([MedicineFill]). En okänd lagrad form ersätts av den valda. */
fun PrnMedicine.filledFrom(entry: MedicineEntry): PrnMedicine = entry.fill.let { fill ->
    copy(name = fill.name, strength = fill.strength, form = fill.form, unknownForm = null, unit = fill.unit)
}

/**
 * Läkemedelsverkets läkemedelsformer ("Filmdragerad tablett", "Kapsel, hård", "Ögondroppar,
 * lösning" …) som [MedicineForm]. Ordningen avgör: en injektion i pulverform är Annat, en
 * inhalationskapsel är Inhalator. Okänt blir Annat.
 */
object MedicineForms {
    private val rules: List<Pair<Regex, MedicineForm>> = listOf(
        "injektion|infusion|dialys|implantat|gas|instillation|spolvätska" to MedicineForm.OTHER,
        "inhal|nebulisator" to MedicineForm.INHALER,
        "plåster" to MedicineForm.PATCH,
        "droppar" to MedicineForm.DROPS,
        "spray|kräm|salva|gel|skum|pasta|suppositori|vagitori|stolpiller|tuggummi|schampo|lack|stift|tampong" to MedicineForm.OTHER,
        "kapsel" to MedicineForm.CAPSULE,
        "tablett|resoriblett" to MedicineForm.TABLET,
        "pulver|granulat" to MedicineForm.POWDER,
        "lösning|suspension|sirap|emulsion|vätska|dryck|mixtur" to MedicineForm.LIQUID,
    ).map { (pattern, form) -> Regex(pattern) to form }

    fun of(formText: String): MedicineForm {
        val text = formText.lowercase()
        return rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second ?: MedicineForm.OTHER
    }
}
