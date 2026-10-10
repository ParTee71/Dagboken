package se.partee71.dagboken.core.medicine

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.HexFormat
import se.partee71.dagboken.core.engine.formatDose
import se.partee71.dagboken.core.engine.parseDose
import se.partee71.dagboken.core.engine.prescriptionRef
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.FORM
import se.partee71.dagboken.core.schema.STRENGTH
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.core.schema.string
import se.partee71.dagboken.core.schema.wireValue

/**
 * Engångsverktyget som ger befintliga recept och vid behov-mediciner namn, styrka och form ur
 * Läkemedelsverkets lista (REC-1, FAV-1), i två steg på en `tools/db export`-fil:
 *
 * 1. [suggest]: en karta (TSV, [renderMap]) med ett förslag per medicin som användaren granskar och rättar.
 * 2. [apply]: den granskade kartan ([parseMap]) → **bara de ändrade fälten** i de berörda dokumenten, för
 *    `tools/db import --update` (Firestores `update`: andra fält – också sådana som ändrats i appen efter
 *    exporten – står kvar, och ett dokument som raderats sedan dess återskapas inte).
 *
 * Läser råa dokument ([Doc]) – aldrig via codecarna – så att inget okänt fält skrivs. Doserna får bara
 * `name` och `strength`; historikens dos och enhet ändras aldrig. Inget innehåll skrivs till loggar –
 * bara antal ([Applied]).
 */
object MedicineRename {

    /** Kartans kolumner, i ordning (rubrikraden). */
    val HEADER: List<String> = listOf(
        "uid", "collection", "id", "oldName", "newName", "strength", "form", "oldDose", "oldUnit", "newDose", "newUnit", "doses",
        "oldStrength", "oldForm", "oldBoosts",
    )

    /**
     * En rad i kartan: medicinen `users/[uid]/[collection]/[id]` och vad den ska få. Tomt [newName] = rör inte; tom
     * [strength] eller [form] = lämna den lagrade. De gamla värdena (namn, styrka, form, dos, enhet och ett
     * fingeravtryck av doshöjningarna, [oldBoosts]) är medicinen vid förslaget – skiljer sig exporten som kartan
     * tillämpas på är raden inaktuell.
     */
    data class Row(
        val uid: String,
        val collection: String,
        val id: String,
        val oldName: String,
        val newName: String = "",
        val strength: String = "",
        val form: String = "",
        val oldDose: String = "",
        val oldUnit: String = "",
        val newDose: String = "",
        val newUnit: String = "",
        val doses: Int = 0,
        val oldStrength: String = "",
        val oldForm: String = "",
        val oldBoosts: String = "",
    ) {
        val path: String get() = CollectionNames.document(uid, collection, id)
    }

    /** Utfallet av [apply]: ändringarna att importera och antal per utfall – aldrig namn eller värden. */
    data class Applied(
        /** Per dokument bara de fält som ändras (`tools/db import --update`). */
        val documents: List<ExportFormat.Document>,
        /** Recept och vid behov-mediciner som ändrats. */
        val medicines: Int,
        /** Doser som fått nytt namn eller ny styrka. */
        val doses: Int,
        /** Rader med tomt nytt namn. */
        val skipped: Int,
        /** Rader vars medicin saknas i exporten eller har ändrats sedan förslaget (namn, styrka, form, dos, enhet eller höjningar). */
        val stale: Int,
        /** Recept vars dos och enhet lämnats orörda: dosen eller en doshöjning går inte att räkna om exakt. */
        val doseKept: Int,
        /** Doser utan koppling vars namn flera mediciner har men inte får samma nya namn och styrka – lämnade orörda. */
        val ambiguousDoses: Int,
    )

    private val MEDICINES = setOf(CollectionNames.PRESCRIPTIONS, CollectionNames.PRN_MEDICINES)

    // ---- Steg 1: förslag ----

    /**
     * Ett förslag per recept och vid behov-medicin i [documents], i exportens ordning ([bestMatch]); ingen
     * träff ger tomma nya fält. Ny dos och enhet räknas om när dosens enhet är styrkans och styrkan är ett
     * tal (dos 500 mg, styrka 500 mg → 1 tablett); annars föreslås de gamla.
     */
    fun suggest(documents: List<ExportFormat.Document>, catalog: MedicineCatalog): List<Row> {
        val medicines = documents.filter(::isMedicine)
        val index = MedicineIndex(medicines)
        val counts = HashMap<String, Int>()
        for (dose in storedDoses(documents)) {
            for (medicine in index.linked(dose) + index.sameName(dose)) counts.merge(medicine.path, 1, Int::plus)
        }
        return medicines.map { doc ->
            val name = doc.data.string("name")
            val dose = doc.data.string("dose")
            val unit = doc.data.string("unit")
            val base = Row(
                userOf(doc.path), collectionOf(doc.path), idOf(doc.path), name, oldDose = dose, oldUnit = unit, doses = counts[doc.path] ?: 0,
                oldStrength = doc.data.string(STRENGTH), oldForm = doc.data.string(FORM), oldBoosts = fingerprint(doc.data["boosts"]),
            )
            val entry = bestMatch(catalog, name, dose, unit) ?: return@map base
            val (newDose, newUnit) = perUnit(dose, unit, entry.strength)?.let { it to entry.form.defaultUnit } ?: (dose to unit)
            base.copy(newName = entry.name, strength = entry.strength, form = entry.form.wire, newDose = newDose, newUnit = newUnit)
        }
    }

    /**
     * Bästa träffen för namnet, annars för namnets första ord: bland träffarna den vars styrka är den lagrade
     * dosen med enhet (dos 500 mg → "500 mg"), annars den första.
     */
    internal fun bestMatch(catalog: MedicineCatalog, name: String, dose: String, unit: String): MedicineEntry? {
        val firstWord = name.trim().substringBefore(' ')
        val queries = listOf(name).plus(firstWord.takeIf { it != name.trim() }).filterNotNull()
        val matches = queries.asSequence().map { catalog.search(it, limit = PREFERENCE_LIMIT) }.firstOrNull { it.isNotEmpty() } ?: return null
        return (matches.firstOrNull { perUnit(dose, unit, it.entry.strength) == "1" } ?: matches.first()).entry
    }

    /**
     * Antalet enheter av styrkan som dosen är (`"500"` mg med styrka `"500 mg"` → `"1"`), när dosens enhet
     * är styrkans och båda är tal och kvoten går jämnt ut i dosformatet ([formatDose]); annars `null`.
     */
    internal fun perUnit(dose: String, unit: String, strength: String): String? {
        val match = STRENGTH_PATTERN.matchEntire(strength.trim()) ?: return null
        if (normalizeUnit(match.groupValues[2]) != normalizeUnit(unit)) return null
        return ratio(dose, BigDecimal.ONE, match.groupValues[1])
    }

    /** `dose × factor / divisor` exakt i dosformatet (högst sex decimaler), annars `null`. */
    private fun ratio(dose: String, factor: BigDecimal, divisor: String): String? {
        val value = decimal(dose) ?: return null
        val by = decimal(divisor)?.takeIf { it.signum() != 0 } ?: return null
        val result = runCatching { value.multiply(factor).divide(by, DOSE_DECIMALS, RoundingMode.UNNECESSARY) }.getOrNull() ?: return null
        return formatDose(result.toDouble())
    }

    private fun decimal(text: String): BigDecimal? = parseDose(text)?.let(BigDecimal::valueOf)

    private fun normalizeUnit(unit: String): String = unit.trim().lowercase().let { MICROGRAM_ALIASES[it] ?: it }

    // ---- Kartan som TSV ----

    /** Kartan med rubrikrad, en rad per medicin, tabbseparerad. Tabb och radbrytning i ett värde blir mellanslag. */
    fun renderMap(rows: List<Row>): String = buildString {
        appendLine(HEADER.joinToString("\t"))
        for (row in rows) {
            val cells = listOf(
                row.uid, row.collection, row.id, row.oldName, row.newName, row.strength, row.form,
                row.oldDose, row.oldUnit, row.newDose, row.newUnit, row.doses.toString(), row.oldStrength, row.oldForm, row.oldBoosts,
            )
            appendLine(cells.joinToString("\t") { it.replace(CONTROL, " ") })
        }
    }

    /**
     * Kartan ur [text] (som [renderMap] skrev den, eventuellt rättad). Ett fel nämner radnumret och skälet,
     * aldrig innehållet: fel rubrik, fel antal kolumner, okänd samling, okänd form, ett antal som inte är ett tal
     * eller samma medicin två gånger.
     */
    fun parseMap(text: String): List<Row> {
        val lines = text.lines().map { it.removeSuffix("\r") }.withIndex().filter { it.value.isNotBlank() }
        require(lines.firstOrNull()?.value?.split('\t') == HEADER) { "rad 1: rubrikraden ska vara ${HEADER.joinToString(" ")}" }
        val seen = mutableSetOf<String>()
        return lines.drop(1).map { (index, line) ->
            val cols = line.split('\t')
            val at = "rad ${index + 1}"
            require(cols.size == HEADER.size) { "$at: ${cols.size} kolumner, ska vara ${HEADER.size}" }
            require(cols[1] in MEDICINES) { "$at: okänd samling" }
            require(cols[6].isEmpty() || wireValue<MedicineForm>(cols[6]) != null) { "$at: okänd form" }
            val doses = requireNotNull(cols[11].toIntOrNull()) { "$at: antalet doser är inte ett tal" }
            Row(
                cols[0], cols[1], cols[2], cols[3], cols[4].trim(), cols[5].trim(), cols[6], cols[7], cols[8], cols[9].trim(), cols[10].trim(), doses,
                cols[12], cols[13], cols[14],
            )
                .also { require(seen.add(it.path)) { "$at: medicinen finns redan på en tidigare rad" } }
        }
    }

    // ---- Steg 2: tillämpa ----

    /**
     * Ändringarna som [rows] gör i [documents], per dokument bara de fält som får ett nytt värde, i exportens ordning:
     * - medicinen får `name` ur raden, `strength` och `form` när raden anger dem (tomt = den lagrade står kvar), och
     *   `dose`/`unit` när raden ändrar dem. Ett recept
     *   med doshöjningar får dem omräknade med samma faktor (ny dos / gammal dos) och receptets nya enhet – går
     *   dosen eller någon höjning inte att räkna om exakt lämnas dos, enhet **och** höjningar orörda;
     * - dess doser – kopplade via `prescriptionId` (eller receptdosens id), `prnId`, eller utan koppling med
     *   medicinens gamla namn – får **bara** medicinens nya `name` och `strength`. En okopplad dos rörs bara när
     *   alla användarens mediciner med det namnet (också de som inte ändras) får samma namn och styrka.
     *
     * En rad med tomt nytt namn hoppas över; en rad vars medicin saknas eller vars namn, styrka, form, dos, enhet
     * eller höjningar ändrats sedan förslaget räknas som inaktuell och hoppas över. De ändrade fälten valideras mot
     * [DocumentRules] (= rules); ett brott stoppar med fältets sökväg, aldrig värdet.
     */
    fun apply(documents: List<ExportFormat.Document>, rows: List<Row>): Applied {
        val medicines = documents.filter(::isMedicine).associateBy { it.path }
        val changes = LinkedHashMap<String, Doc>()
        val targets = HashMap<String, Target>()
        var skipped = 0
        var stale = 0
        var doseKept = 0
        for (row in rows) {
            if (row.newName.isEmpty()) {
                skipped++
                continue
            }
            val doc = medicines[row.path]?.takeIf { isCurrent(it.data, row) }
            if (doc == null) {
                stale++
                continue
            }
            val (fields, keptDose) = renamedMedicine(doc, row)
            if (keptDose) doseKept++
            changes[doc.path] = fields
            targets[doc.path] = Target(row.newName, row.strength.ifEmpty { doc.data.string(STRENGTH) })
        }
        val doseChanges = renamedDoses(documents, medicines.values, targets)
        changes += doseChanges.changes
        val byPath = documents.associateBy { it.path }
        val updates = documents.mapNotNull { doc ->
            changes[doc.path]?.let { all -> all.filter { (key, value) -> differs(doc.data, key, value) } }
                ?.takeIf { it.isNotEmpty() }
                ?.let { ExportFormat.Document(doc.path, it) }
        }
        validate(updates, byPath)
        return Applied(
            documents = updates,
            medicines = updates.count { isMedicine(it) },
            doses = updates.count { collectionOf(it.path) == CollectionNames.DOSES },
            skipped = skipped,
            stale = stale,
            doseKept = doseKept,
            ambiguousDoses = doseChanges.ambiguous,
        )
    }

    /**
     * Om fältet [key] får ett annat värde än det lagrade, jämfört som i exportfilen. Saknat är `null`, och en tom
     * styrka på ett dokument utan styrka (alla från 3.x) är ingen ändring.
     */
    private fun differs(stored: Doc, key: String, value: Any?): Boolean =
        !(stored[key] == null && value == "") && ExportFormat.toJson(stored[key]) != ExportFormat.toJson(value)

    /** Om medicinen är som när förslaget gjordes: samma namn, styrka, form, dos, enhet och höjningar som radens gamla. */
    private fun isCurrent(data: Doc, row: Row): Boolean =
        data.string("name") == row.oldName && data.string(STRENGTH) == row.oldStrength && data.string(FORM) == row.oldForm &&
            data.string("dose") == row.oldDose && data.string("unit") == row.oldUnit && fingerprint(data["boosts"]) == row.oldBoosts

    /**
     * Fingeravtrycket av doshöjningarna i kartan: de första tecknen av SHA-256 över den kanoniska JSON-formen (sorterade
     * nycklar); tomt när fältet saknas. Säger bara om höjningarna ändrats – aldrig vad de är.
     */
    internal fun fingerprint(boosts: Any?): String {
        if (boosts == null) return ""
        val text = ExportFormat.toJson(canonical(boosts)).toString()
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))).take(FINGERPRINT_LENGTH)
    }

    private fun canonical(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.associate { it.key.toString() to canonical(it.value) }
        is List<*> -> value.map(::canonical)
        else -> value
    }

    /** Medicinens nya fält (före jämförelsen med de lagrade) och om dos och enhet fick stå kvar trots att raden ändrade dem. */
    private fun renamedMedicine(doc: ExportFormat.Document, row: Row): Pair<Doc, Boolean> {
        // Tom styrka eller form i kartan = oförändrad: ett lagrat värde nollställs aldrig.
        val named = mapOf("name" to row.newName) +
            listOfNotNull(row.strength.takeIf { it.isNotEmpty() }?.let { STRENGTH to it }, row.form.takeIf { it.isNotEmpty() }?.let { FORM to it })
        val doseChanges = (row.newDose.isNotEmpty() || row.newUnit.isNotEmpty()) && (row.newDose != row.oldDose || row.newUnit != row.oldUnit)
        if (!doseChanges) return named to false
        val withDose = named + mapOf("dose" to row.newDose, "unit" to row.newUnit)
        if (collectionOf(doc.path) != CollectionNames.PRESCRIPTIONS || doc.data["boosts"] !is List<*>) return withDose to false
        val boosts = rescaledBoosts(doc.data["boosts"] as List<*>, row) ?: return named to true
        return (withDose + ("boosts" to boosts)) to false
    }

    /** Höjningarna × (ny dos / gammal dos) med den nya enheten, eller `null` när någon inte går jämnt ut. */
    private fun rescaledBoosts(boosts: List<*>, row: Row): List<Doc>? {
        if (boosts.isEmpty()) return emptyList()
        val factor = decimal(row.newDose) ?: return null
        return boosts.map { boost ->
            if (boost !is Map<*, *>) return null
            val doc = asDoc(boost)
            val dose = ratio(doc.string("dose"), factor, row.oldDose) ?: return null
            doc + mapOf("dose" to dose, "unit" to row.newUnit)
        }
    }

    /** Vad en medicin heter efter ändringen: namn och styrka – det dess doser får. */
    private data class Target(val name: String, val strength: String)

    private class DoseChanges(val changes: Map<String, Doc>, val ambiguous: Int)

    /**
     * Dosernas nya `name` och `strength`: en kopplad dos får sin omdöpta medicins; en dos utan koppling får dem
     * bara när **alla** användarens mediciner med dess namn – omdöpta eller inte – hamnar på samma namn och styrka.
     */
    private fun renamedDoses(
        documents: List<ExportFormat.Document>,
        medicines: Collection<ExportFormat.Document>,
        targets: Map<String, Target>,
    ): DoseChanges {
        val changes = LinkedHashMap<String, Doc>()
        var ambiguous = 0
        val index = MedicineIndex(medicines)
        val after = { medicine: ExportFormat.Document ->
            targets[medicine.path] ?: Target(medicine.data.string("name"), medicine.data.string(STRENGTH))
        }
        for (dose in storedDoses(documents)) {
            val target = if (!dose.isUnlinked) {
                index.linked(dose).firstNotNullOfOrNull { targets[it.path] }
            } else {
                index.sameName(dose).takeIf { same -> same.any { it.path in targets } }?.map(after)?.distinct()?.let { candidates ->
                    if (candidates.size > 1) ambiguous++
                    candidates.singleOrNull()
                }
            }
            if (target != null) changes[dose.path] = mapOf(DoseCodec.NAME to target.name, DoseCodec.STRENGTH to target.strength)
        }
        return DoseChanges(changes, ambiguous)
    }

    /** Brott mot rules i de fält [updates] skriver, prövade på hela dokumentet efter ändringen. */
    private fun validate(updates: List<ExportFormat.Document>, stored: Map<String, ExportFormat.Document>) {
        val violations = updates.flatMap { update ->
            DocumentRules.validate(collectionOf(update.path), stored.getValue(update.path).data + update.data)
                .filter { it.field.substringBefore('.').substringBefore('[') in update.data.keys }
                .map { "${update.path}: ${it.field} ${it.reason}" }
        }
        require(violations.isEmpty()) { violations.joinToString("\n") }
    }

    // ---- Doser och sökvägar ----

    /** En dos som rå och avkodad – avkodningen bara för att läsa kopplingen och namnet. */
    private class StoredDose(val path: String, val name: String, val prescriptionRef: String?, val prnId: String?) {
        val user: String get() = userOf(path)
        val isUnlinked: Boolean get() = prescriptionRef == null && prnId == null
    }

    private fun storedDoses(documents: List<ExportFormat.Document>): List<StoredDose> =
        documents.filter { isDirectChild(it.path) && collectionOf(it.path) == CollectionNames.DOSES }.map { doc ->
            val dose = DoseCodec.decode(idOf(doc.path), doc.data)
            StoredDose(doc.path, dose.name, dose.prescriptionRef, dose.prnId)
        }

    /** Medicinerna uppslagna på sökväg och på (användare, namn) – en dos hittar sina på en gång, aldrig genom att gå igenom alla. */
    private class MedicineIndex(medicines: Collection<ExportFormat.Document>) {
        private val byPath = medicines.associateBy { it.path }
        private val byName = medicines.groupBy { userOf(it.path) to it.data.string("name") }

        /** Receptet (via `prescriptionId` eller receptdosens id) och vid behov-medicinen (`prnId`) dosen är kopplad till. */
        fun linked(dose: StoredDose): List<ExportFormat.Document> = listOfNotNull(
            dose.prescriptionRef?.let { byPath[CollectionNames.document(dose.user, CollectionNames.PRESCRIPTIONS, it)] },
            dose.prnId?.let { byPath[CollectionNames.document(dose.user, CollectionNames.PRN_MEDICINES, it)] },
        )

        /** Användarens mediciner med samma namn som en dos utan koppling; tom för en kopplad dos. */
        fun sameName(dose: StoredDose): List<ExportFormat.Document> = if (dose.isUnlinked) byName[dose.user to dose.name].orEmpty() else emptyList()
    }

    private fun isMedicine(doc: ExportFormat.Document): Boolean = isDirectChild(doc.path) && collectionOf(doc.path) in MEDICINES

    /** `users/{uid}/{samling}/{id}` – inte användardokumentet och inte incheckningarna. */
    private fun isDirectChild(path: String): Boolean = path.count { it == '/' } == 3

    private fun collectionOf(path: String): String = CollectionNames.collectionOf(path)

    private fun idOf(path: String): String = path.substringAfterLast('/')

    private fun userOf(path: String): String = path.split('/')[1]

    private const val FINGERPRINT_LENGTH = 12

    /** `"500 mg"`, `"0,5 mg/dos"` → talet och enheten. */
    private val STRENGTH_PATTERN = Regex("""([0-9][0-9.,]*)\s*(\S.*)""")

    /** Mikrogram skrivs på flera sätt; Läkemedelsverket skriver "mikrogram", 3.x-enheterna "mcg". */
    private val MICROGRAM_ALIASES = listOf("µg", "μg", "mcg", "ug", "mikrogram").associateWith { "µg" }

    /** Så många träffar prövas för att hitta den vars styrka är den lagrade dosen. */
    private const val PREFERENCE_LIMIT = 50

    private val CONTROL = Regex("[\t\r\n]")
    private const val DOSE_DECIMALS = 6
}
