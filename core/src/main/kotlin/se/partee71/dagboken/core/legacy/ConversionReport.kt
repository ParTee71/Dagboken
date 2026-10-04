package se.partee71.dagboken.core.legacy

import kotlin.time.Instant
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.Schema

/**
 * Något som hindrar konverteringen (OMB-3): dokumentets sökväg under användaren (`doses/{id}`),
 * fältet och skälet – värdets längd, talet eller enum-namnet, aldrig textinnehållet (hälsodata).
 */
data class Problem(val path: String, val field: String, val reason: String) {
    override fun toString() = "$path · $field: $reason"
}

/** Något som konverterades men är värt att veta (t.ex. en avvikande `somatiska`); aldrig textinnehåll. */
data class Warning(val path: String, val message: String) {
    override fun toString() = "$path: $message"
}

/** Resultatet i siffror: antal dokument per samling, varningar och fel. [render] är texten för stdout. */
data class ConversionReport(
    /** Backupfilens formatversion (1 eller 2). */
    val formatVersion: Int,
    /** Backupfilens `createdAt` som den står (filens metadata). */
    val createdAt: String,
    /** Dokument per samling i `CollectionNames.ALL`-ordning (bara de med dokument). */
    val counts: Map<String, Int>,
    val warnings: List<Warning>,
    val problems: List<Problem>,
) {
    val stopped: Boolean get() = problems.isNotEmpty()

    fun render(): String = buildString {
        appendLine("3.x-backup, format v$formatVersion${if (createdAt.isNotBlank()) ", skapad $createdAt" else ""}")
        appendLine("Dokument per samling:")
        counts.forEach { (collection, count) -> appendLine("  $collection: $count") }
        appendLine("  totalt: ${counts.values.sum()}")
        appendLine("Varningar: ${warnings.size}")
        warnings.forEach { appendLine("  $it") }
        if (stopped) {
            appendLine("Stopp: ${problems.size} fel – ingenting konverterades")
            problems.forEach { appendLine("  $it") }
        } else {
            appendLine("Inga fel – alla dokument ryms i TextLimits och firestore.rules.")
        }
    }
}

/** De konverterade dokumenten som typade modeller (för batchskrivning via `FirestoreCollection`). */
data class ConvertedData(
    /** `settings/app` med 3.x-defaults där backupen saknade värdet; `null` om backupen inte hade några inställningar alls. */
    val settings: Settings?,
    val options: List<Option>,
    val prescriptions: List<Prescription>,
    val prnMedicines: List<PrnMedicine>,
    val doses: List<Dose>,
    val screenings: List<Screening>,
    val activities: List<Activity>,
    val events: List<Event>,
    val episodes: List<IllnessEpisode>,
    /** Incheckningar per episod-id. */
    val checkins: Map<String, List<Checkin>>,
)

/** Utfallet: antingen alla dokument eller ett stopp – aldrig en del. */
sealed interface ConversionResult {
    val report: ConversionReport

    /**
     * Lyckad konvertering. [documents] är sorterade på sökväg och färdiga för `tools/db import.mjs`
     * ([exportJson]) eller för batchskrivning; inställningsdokumentet innehåller bara de fält backupen
     * hade (ARKITEKTUR.md → Fältparitet, `BackupJson.settings`). Legacy-läsaren på enheten skriver det med
     * merge (etapp 3); `tools/db import.mjs` skriver dokumenten som de är och körs därför mot ett tomt
     * scratch-uid (ARKITEKTUR.md → Migrering, punkt 3).
     */
    data class Converted(
        val data: ConvertedData,
        val documents: List<ExportFormat.Document>,
        override val report: ConversionReport,
    ) : ConversionResult {
        /** Filen i `tools/db export`-format med dagens `schemaVersion`. */
        fun exportJson(exportedAt: Instant): String = ExportFormat.encode(exportedAt, Schema.CURRENT_VERSION, documents)
    }

    /** Något ryms inte eller saknar plats; rapporten listar varje fel. Inget dokument lämnas ut. */
    data class Stopped(override val report: ConversionReport) : ConversionResult
}
