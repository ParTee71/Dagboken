package se.partee71.dagboken.domain.usecase

import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.coversDate
import se.partee71.dagboken.domain.model.dosFor
import se.partee71.dagboken.domain.model.dosperiodEnd
import se.partee71.dagboken.domain.model.dosperiodFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Ett periodslut som ska påminnas om dagen innan (NOT-12). */
sealed interface PeriodSlut {
    val receptNamn: String

    /** Receptets hela period tar slut — sista dosen tas denna dag. */
    data class ReceptSlut(override val receptNamn: String) : PeriodSlut

    /** En tillfällig dosändring tar slut; [nyDos] är dosen som gäller dagen efter. */
    data class DosperiodSlut(override val receptNamn: String, val nyDos: String) : PeriodSlut
}

/**
 * Hittar de periodslut som infaller på ett givet datum, så att påminnelsen kan skickas
 * dagen innan (NOT-12). Ren logik — ingen Android- eller I/O-inblandning.
 */
class PeriodEndingsUseCase @Inject constructor() {

    fun endingOn(recept: List<Recept>, date: LocalDate): List<PeriodSlut> =
        recept.filter { it.aktiv }.mapNotNull { r ->
            val receptEnd = parseIso(r.slutDatum)
            if (receptEnd == date) return@mapNotNull PeriodSlut.ReceptSlut(r.namn)

            // Receptet fortsätter, men den dosperiod som gäller idag gör det inte.
            val aktivDosperiod = r.dosperiodFor(date) ?: return@mapNotNull null
            if (r.dosperiodEnd(aktivDosperiod) != date) return@mapNotNull null
            if (!r.coversDate(date.plusDays(1))) return@mapNotNull null

            val (dos, enhet) = r.dosFor(date.plusDays(1))
            PeriodSlut.DosperiodSlut(r.namn, "$dos $enhet".trim())
        }

    private fun parseIso(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        }
}
