package se.partee71.dagboken.core.engine

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import se.partee71.dagboken.core.model.Prescription

// Periodslut (NOT-12, MEDF-2) – port av 3.x `PeriodEndingsUseCase`. Påminnelsen dagen innan och
// bannern i fliken Mediciner räknar här.

/** Ett periodslut: sista dagen [date] för receptet [prescriptionId]. */
sealed interface PeriodEnding {
    val prescriptionId: String
    val name: String
    val date: LocalDate

    /** Receptets hela period tar slut (REC-7) – sista dosen tas [date]. */
    data class PrescriptionEnds(
        override val prescriptionId: String,
        override val name: String,
        override val date: LocalDate,
    ) : PeriodEnding

    /**
     * En doshöjning tar slut (REC-9) men receptet fortsätter; [newDose] i [unit] är den totala dosen
     * dagen efter – grunddosen eller nästa höjning (REC-12).
     */
    data class BoostEnds(
        override val prescriptionId: String,
        override val name: String,
        override val date: LocalDate,
        val newDose: String,
        val unit: String,
    ) : PeriodEnding
}

/**
 * NOT-12: de aktiva recepten vars period eller pågående höjning har sin sista dag [date]. För receptet
 * är det den sista dagen som faktiskt ger en dos ([lastDoseDay]) – ett helgrecept som slutar en onsdag
 * slutar i praktiken söndagen före; detsamma gäller höjningens slut ([lastDoseDayOf]). En höjning rapporteras bara när receptet ger en dos efter den
 * ([nextDoseDayAfter]) – annars är det receptets slut som gäller (och okänd upprepning ger ingen dos);
 * en höjning som löper till periodens slut rapporteras alltså aldrig för sig. Den nya dosen är den
 * som gäller nästa dosdag.
 */
fun List<Prescription>.endingOn(date: LocalDate): List<PeriodEnding> = filter { it.active }.mapNotNull { p ->
    if (p.lastDoseDay() == date) return@mapNotNull PeriodEnding.PrescriptionEnds(p.id, p.name, date)
    val boost = p.boostFor(date) ?: return@mapNotNull null
    if (p.lastDoseDayOf(boost) != date) return@mapNotNull null
    val next = p.nextDoseDayAfter(date) ?: return@mapNotNull null
    PeriodEnding.BoostEnds(p.id, p.name, date, p.doseFor(next), p.unit)
}

/** MEDF-2: periodslut idag och i morgon, i den ordningen. */
fun List<Prescription>.endingSoon(today: LocalDate): List<PeriodEnding> =
    endingOn(today) + endingOn(today.plus(1, DateTimeUnit.DAY))
