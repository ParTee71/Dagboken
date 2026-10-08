package se.partee71.dagboken.core.legacy

import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat

/**
 * Batchformen för migreringen på enheten (OMB-2, ARKITEKTUR.md → Risker): konverterarens dokument delas i
 * batchar om högst [MAX_WRITES] skrivningar (Firestores gräns) **och** högst [MAX_EPISODES] distinkta
 * episoder, där en episod alltid ligger i samma batch som sina incheckningar – rules kräver att episoden
 * finns efter skrivningen (`existsAfter`) och tillåter högst 20 dokumentuppslag per batch. Ordningen bevaras,
 * så i konverterarens sökvägsordning kommer episoden före sina incheckningar; en episod med fler
 * incheckningar än som ryms delas upp med episoden i den första biten. Ren funktion, testad i `:core`.
 */
object MigrationBatches {
    const val MAX_WRITES = 500
    const val MAX_EPISODES = 20

    fun plan(
        documents: List<ExportFormat.Document>,
        maxWrites: Int = MAX_WRITES,
        maxEpisodes: Int = MAX_EPISODES,
    ): List<List<ExportFormat.Document>> {
        require(maxWrites > 0 && maxEpisodes > 0) { "batchgränserna måste vara positiva" }
        val batches = mutableListOf<List<ExportFormat.Document>>()
        var current = mutableListOf<ExportFormat.Document>()
        var episodes = 0
        fun flush() {
            if (current.isNotEmpty()) batches += current
            current = mutableListOf()
            episodes = 0
        }
        for (unit in units(documents)) {
            val isEpisode = unit.any(::belongsToEpisode)
            if (unit.size > maxWrites) {
                // Episoden först, så att varje senare bit slår upp en episod som redan finns.
                flush()
                unit.chunked(maxWrites).forEach { batches += it }
                continue
            }
            if (current.size + unit.size > maxWrites || (isEpisode && episodes + 1 > maxEpisodes)) flush()
            current += unit
            if (isEpisode) episodes++
        }
        flush()
        return batches
    }

    /** Dokumenten i enheter som inte får delas: en episod med sina incheckningar, annars ett dokument var. */
    private fun units(documents: List<ExportFormat.Document>): List<List<ExportFormat.Document>> =
        documents.groupBy(::unitKey).values.toList()

    /** Episodens sökväg för episoden själv och dess incheckningar; andra dokument är sin egen enhet. */
    private fun unitKey(document: ExportFormat.Document): String = when (CollectionNames.collectionOf(document.path)) {
        CollectionNames.CHECKINS -> document.path.split('/').dropLast(2).joinToString("/")
        else -> document.path
    }

    private fun belongsToEpisode(document: ExportFormat.Document): Boolean =
        CollectionNames.collectionOf(document.path).let { it == CollectionNames.CHECKINS || it == CollectionNames.ILLNESS_EPISODES }
}
