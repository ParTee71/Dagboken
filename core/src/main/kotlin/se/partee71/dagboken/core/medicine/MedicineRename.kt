package se.partee71.dagboken.core.medicine

import java.math.BigDecimal
import java.math.RoundingMode
import se.partee71.dagboken.core.engine.formatDose
import se.partee71.dagboken.core.engine.parseDose
import se.partee71.dagboken.core.engine.prescriptionRef
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.STRENGTH
import se.partee71.dagboken.core.schema.FORM
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.core.schema.string
import se.partee71.dagboken.core.schema.wireValue

/**
 * Engångsverktyget som ger befintliga recept och vid behov-mediciner namn, styrka och form ur
 * Läkemedelsverkets lista (REC-1, FAV-1), i två steg på en `tools/db export`-fil:
 *
 * 1. [suggest]: en karta (TSV, [renderMap]) med ett förslag per medicin som användaren granskar och rättar.
 * 2. [apply]: den granskade kartan ([parseMap]) → **bara** de ändrade dokumenten, råa, för `tools/db import`.
 *
 * Arbetar på råa dokument ([Doc]) – aldrig via codecarna – så att alla andra och okända fält följer med
 * oförändrade (import skriver hela dokumentet utan merge). Doserna får bara nytt `name`; historikens dos och
 * enhet ändras aldrig. Inget innehåll skrivs till loggar – bara antal ([Applied]).
 */
object MedicineRename {

    /** Kartans kolumner, i ordning (rubrikraden). */
    val HEADER: List<String> = listOf(
        "collection", "id", "oldName", "newName", "strength", "form", "oldDose", "oldUnit", "newDose", "newUnit", "doses",
    )

    /** En rad i kartan: medicinen [collection]/[id] och vad den ska få. Tomt [newName] = rör inte. */
    data class Row(
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
    )

    /** Utfallet av [apply]: dokumenten att importera och antal per utfall – aldrig namn eller värden. */
    data class Applied(
        val documents: List<ExportFormat.Document>,
        /** Recept och vid behov-mediciner som ändrats. */
        val medicines: Int,
        /** Doser som fått nytt namn. */
        val doses: Int,
        /** Rader med tomt nytt namn. */
        val skipped: Int,
        /** Rader vars medicin saknas i exporten eller har ändrats sedan förslaget (namn, dos eller enhet). */
        val stale: Int,
        /** Recept vars dos och enhet lämnats orörda: dosen eller en doshöjning går inte att räkna om exakt. */
        val doseKept: Int,
        /** Doser utan koppling vars namn flera rader ger olika nya namn – lämnade orörda. */
        val ambiguousDoses: Int,
    )

    private val MEDICINES = setOf(CollectionNames.PRESCRIPTIONS, CollectionNames.PRN_MEDICINES)

    // ---- Steg 1: förslag ----

    /**
     * Ett förslag per recept och vid behov-medicin i [documents], i exportens ordning: första träffen för
     * namnet i [catalog], annars för namnets första ord; ingen träff ger tomma nya fält. Ny dos och enhet
     * räknas om när dosens enhet är styrkans och styrkan är ett tal (dos 500 mg, styrka 500 mg → 1 tablett);
     * annars föreslås de gamla.
     */
    fun suggest(documents: List<ExportFormat.Document>, catalog: MedicineCatalog): List<Row> {
        val doses = dosesByUser(documents)
        return documents.filter(::isMedicine).map { doc ->
            val name = doc.data.string("name")
            val dose = doc.data.string("dose")
            val unit = doc.data.string("unit")
            val count = doses[userOf(doc.path)].orEmpty().count { belongsTo(it, doc, name) }
            val base = Row(collectionOf(doc.path), idOf(doc.path), name, oldDose = dose, oldUnit = unit, doses = count)
            val entry = bestMatch(catalog, name) ?: return@map base
            val (newDose, newUnit) = perUnit(dose, unit, entry.strength)?.let { it to entry.form.defaultUnit } ?: (dose to unit)
            base.copy(newName = entry.name, strength = entry.strength, form = entry.form.wire, newDose = newDose, newUnit = newUnit)
        }
    }

    private fun bestMatch(catalog: MedicineCatalog, name: String): MedicineEntry? {
        catalog.search(name).firstOrNull()?.let { return it.entry }
        val firstWord = name.trim().substringBefore(' ')
        return if (firstWord != name.trim()) catalog.search(firstWord).firstOrNull()?.entry else null
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
                row.collection, row.id, row.oldName, row.newName, row.strength, row.form,
                row.oldDose, row.oldUnit, row.newDose, row.newUnit, row.doses.toString(),
            )
            appendLine(cells.joinToString("\t") { it.replace(CONTROL, " ") })
        }
    }

    /**
     * Kartan ur [text] (som [renderMap] skrev den, eventuellt rättad). Ett fel nämner radnumret och skälet,
     * aldrig innehållet: fel rubrik, fel antal kolumner, okänd samling, okänd form eller ett antal som inte är ett tal.
     */
    fun parseMap(text: String): List<Row> {
        val lines = text.lines().map { it.removeSuffix("\r") }.withIndex().filter { it.value.isNotBlank() }
        require(lines.firstOrNull()?.value?.split('\t') == HEADER) { "rad 1: rubrikraden ska vara ${HEADER.joinToString(" ")}" }
        return lines.drop(1).map { (index, line) ->
            val cols = line.split('\t')
            val at = "rad ${index + 1}"
            require(cols.size == HEADER.size) { "$at: ${cols.size} kolumner, ska vara ${HEADER.size}" }
            require(cols[0] in MEDICINES) { "$at: okänd samling" }
            require(cols[5].isEmpty() || wireValue<MedicineForm>(cols[5]) != null) { "$at: okänd form" }
            val doses = requireNotNull(cols[10].toIntOrNull()) { "$at: antalet doser är inte ett tal" }
            Row(cols[0], cols[1], cols[2], cols[3].trim(), cols[4].trim(), cols[5], cols[6], cols[7], cols[8].trim(), cols[9].trim(), doses)
        }
    }

    // ---- Steg 2: tillämpa ----

    /**
     * De dokument i [documents] som [rows] ändrar, råa och i exportens ordning:
     * - medicinen får `name`, `strength` och `form` ur raden, och `dose`/`unit` när raden ändrar dem. Ett recept
     *   med doshöjningar får dem omräknade med samma faktor (ny dos / gammal dos) och receptets nya enhet – går
     *   dosen eller någon höjning inte att räkna om exakt lämnas dos, enhet **och** höjningar orörda;
     * - dess doser – kopplade via `prescriptionId` (eller receptdosens id), `prnId`, eller utan koppling med
     *   medicinens gamla namn – får **bara** nytt `name`.
     *
     * En rad med tomt nytt namn hoppas över; en rad vars medicin saknas eller vars namn, dos eller enhet
     * ändrats sedan förslaget räknas som inaktuell och hoppas över. De ändrade fälten valideras mot
     * [DocumentRules] (= rules); ett brott stoppar med fältets sökväg, aldrig värdet.
     */
    fun apply(documents: List<ExportFormat.Document>, rows: List<Row>): Applied {
        val byKey = documents.filter(::isMedicine).groupBy { collectionOf(it.path) to idOf(it.path) }
        val changed = LinkedHashMap<String, Doc>()
        var skipped = 0
        var stale = 0
        var doseKept = 0
        val renamed = mutableListOf<Pair<ExportFormat.Document, String>>()
        for (row in rows) {
            if (row.newName.isEmpty()) {
                skipped++
                continue
            }
            val targets = byKey[row.collection to row.id].orEmpty().filter { isCurrent(it.data, row) }
            if (targets.isEmpty()) stale++
            for (doc in targets) {
                val (data, keptDose) = renamedMedicine(doc, row)
                if (keptDose) doseKept++
                changed[doc.path] = data
                renamed += doc to row.newName
            }
        }
        val doseNames = renamedDoses(documents, renamed)
        val byPath = documents.associateBy { it.path }
        for ((path, name) in doseNames.names) changed[path] = byPath.getValue(path).data + (DoseCodec.NAME to name)
        validate(changed)
        return Applied(
            documents = documents.filter { it.path in changed }.map { ExportFormat.Document(it.path, changed.getValue(it.path)) },
            medicines = renamed.size,
            doses = doseNames.names.size,
            skipped = skipped,
            stale = stale,
            doseKept = doseKept,
            ambiguousDoses = doseNames.ambiguous,
        )
    }

    /** Om medicinen är som när förslaget gjordes: samma namn, dos och enhet som radens gamla. */
    private fun isCurrent(data: Doc, row: Row): Boolean =
        data.string("name") == row.oldName && data.string("dose") == row.oldDose && data.string("unit") == row.oldUnit

    /** Medicinens nya data och om dos och enhet fick stå kvar trots att raden ändrade dem. */
    private fun renamedMedicine(doc: ExportFormat.Document, row: Row): Pair<Doc, Boolean> {
        val named = doc.data + mapOf("name" to row.newName, STRENGTH to row.strength, FORM to row.form.ifEmpty { null })
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

    private class DoseNames(val names: Map<String, String>, val ambiguous: Int)

    /**
     * De nya namnen på doserna till [renamed] (medicin → nytt namn): kopplade doser får sin medicins namn; en
     * dos utan koppling får namnet bara när alla mediciner med dess namn (hos samma användare) får samma nya.
     */
    private fun renamedDoses(documents: List<ExportFormat.Document>, renamed: List<Pair<ExportFormat.Document, String>>): DoseNames {
        val names = LinkedHashMap<String, String>()
        var ambiguous = 0
        val doses = dosesByUser(documents)
        for ((user, userDoses) in doses) {
            val mine = renamed.filter { userOf(it.first.path) == user }
            val byOldName = mine.groupBy({ it.first.data.string("name") }, { it.second })
            for (dose in userDoses) {
                val linked = mine.firstOrNull { (medicine, _) -> isLinkedTo(dose, medicine) }
                val newName = when {
                    linked != null -> linked.second
                    isUnlinked(dose) -> byOldName[dose.name]?.distinct()?.let { candidates ->
                        if (candidates.size > 1) ambiguous++
                        candidates.singleOrNull()
                    }
                    else -> null
                }
                if (newName != null && newName != dose.name) names[dose.path] = newName
            }
        }
        return DoseNames(names, ambiguous)
    }

    private fun validate(changed: Map<String, Doc>) {
        val written = setOf("name", STRENGTH, FORM, "dose", "unit", "boosts")
        val violations = changed.flatMap { (path, data) ->
            DocumentRules.validate(collectionOf(path), data)
                .filter { it.field.substringBefore('.').substringBefore('[') in written }
                .map { "$path: ${it.field} ${it.reason}" }
        }
        require(violations.isEmpty()) { violations.joinToString("\n") }
    }

    // ---- Doser och sökvägar ----

    /** En dos som rå och avkodad – avkodningen bara för att läsa kopplingen och namnet. */
    private class StoredDose(val path: String, val name: String, val prescriptionRef: String?, val prnId: String?)

    private fun dosesByUser(documents: List<ExportFormat.Document>): Map<String, List<StoredDose>> =
        documents.filter { isDirectChild(it.path) && collectionOf(it.path) == CollectionNames.DOSES }
            .map { doc ->
                val dose = DoseCodec.decode(idOf(doc.path), doc.data)
                StoredDose(doc.path, dose.name, dose.prescriptionRef, dose.prnId)
            }
            .groupBy { userOf(it.path) }

    private fun isLinkedTo(dose: StoredDose, medicine: ExportFormat.Document): Boolean = when (collectionOf(medicine.path)) {
        CollectionNames.PRESCRIPTIONS -> dose.prescriptionRef == idOf(medicine.path)
        else -> dose.prnId == idOf(medicine.path)
    }

    private fun isUnlinked(dose: StoredDose): Boolean = dose.prescriptionRef == null && dose.prnId == null

    private fun belongsTo(dose: StoredDose, medicine: ExportFormat.Document, name: String): Boolean =
        isLinkedTo(dose, medicine) || (isUnlinked(dose) && dose.name == name)

    private fun isMedicine(doc: ExportFormat.Document): Boolean = isDirectChild(doc.path) && collectionOf(doc.path) in MEDICINES

    /** `users/{uid}/{samling}/{id}` – inte användardokumentet och inte incheckningarna. */
    private fun isDirectChild(path: String): Boolean = path.count { it == '/' } == 3

    private fun collectionOf(path: String): String = CollectionNames.collectionOf(path)

    private fun idOf(path: String): String = path.substringAfterLast('/')

    private fun userOf(path: String): String = path.split('/')[1]

    /** `"500 mg"`, `"0,5 mg/dos"` → talet och enheten. */
    private val STRENGTH_PATTERN = Regex("""([0-9][0-9.,]*)\s*(\S.*)""")

    /** Mikrogram skrivs på flera sätt; Läkemedelsverket skriver "mikrogram", 3.x-enheterna "mcg". */
    private val MICROGRAM_ALIASES = listOf("µg", "μg", "mcg", "ug", "mikrogram").associateWith { "µg" }

    private val CONTROL = Regex("[\t\r\n]")
    private const val DOSE_DECIMALS = 6
}
