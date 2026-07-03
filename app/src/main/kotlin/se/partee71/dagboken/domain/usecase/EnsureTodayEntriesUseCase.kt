package se.partee71.dagboken.domain.usecase

import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.TIDP_DEFAULT_TIMES
import se.partee71.dagboken.domain.model.Upprepning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class EnsureTodayEntriesUseCase @Inject constructor() {

    /**
     * Computes which Medicin entries need to be created for today based on active Recept.
     * Returns only the new entries that are NOT already in [existingEntries].
     * Stable ID format: recept_{receptId}_{datum}_{tidpunkt}
     *
     * Ports _doEnsureTodayEntries() from src/storage/mediciner.ts exactly.
     */
    fun compute(
        recept: List<Recept>,
        existingEntries: List<Medicin>,
        today: LocalDate = LocalDate.now(),
    ): List<Medicin> {
        val datum    = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val existingIds = existingEntries.map { it.id }.toHashSet()

        val newEntries = mutableListOf<Medicin>()

        for (r in recept) {
            if (!r.aktiv) continue
            if (!shouldTakeToday(r, today)) continue

            val tidpunkter = r.tidpunkter.ifEmpty { listOf("Morgon") }
            for (tidpunkt in tidpunkter) {
                val stableId = "recept_${r.id}_${datum}_${tidpunkt}"
                if (stableId in existingIds) continue

                val tid = TIDP_DEFAULT_TIMES[tidpunkt] ?: "12:00"
                val timestamp = "${datum}T${tid}:00.000Z"

                newEntries += Medicin(
                    id         = stableId,
                    timestamp  = timestamp,
                    datum      = datum,
                    tid        = tid,
                    namn       = r.namn,
                    dos        = r.dos,
                    enhet      = r.enhet,
                    tidpunkt   = tidpunkt,
                    tagen      = false,
                    receptId   = r.id,
                    skipped    = false,
                )
            }
        }

        return newEntries
    }

    /**
     * Ports shouldTakeToday() from src/storage/mediciner.ts exactly.
     * dayIdx: 0=Monday … 6=Sunday (JS getDay() adjusted)
     */
    fun shouldTakeToday(recept: Recept, today: LocalDate = LocalDate.now()): Boolean {
        // Java DayOfWeek: MONDAY=1 … SUNDAY=7; convert to 0-based Mon=0
        val dayIdx = today.dayOfWeek.value - 1
        return when (Upprepning.fromString(recept.upprepning)) {
            Upprepning.VARDAGAR -> dayIdx <= 4
            Upprepning.HELGER   -> dayIdx >= 5
            Upprepning.ANPASSAD -> dayIdx in recept.dagar
            Upprepning.INTERVALL -> {
                val n = recept.intervalDagar.takeIf { it > 1 } ?: return true
                val start = runCatching {
                    LocalDate.parse(recept.skapad, DateTimeFormatter.ISO_LOCAL_DATE)
                }.getOrDefault(today)
                val diffDays = ChronoUnit.DAYS.between(start, today)
                diffDays % n == 0L
            }
            Upprepning.DAGLIGEN -> true
        }
    }
}
