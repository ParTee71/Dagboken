package se.partee71.dagboken.ui

import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot

/** Påhittade recept som skärmtesterna för Mediciner och Idag delar (regel 4 gäller även tester). */
object SampleMedicines {
    val levaxin = Prescription("l", "Levaxin", "100", "µg", listOf(Slot.MORNING), Schedule.Repeating(), Period(LocalDate(2026, 1, 1)))

    /** Grunddos 50 mg med en höjning på 25 mg 29 sep – 12 okt 2026 (REC-9, REC-12). */
    val sertralin = Prescription(
        "s", "Sertralin", "50", "mg", listOf(Slot.MORNING), Schedule.Repeating(), Period(LocalDate(2026, 3, 1)),
        boosts = listOf(Boost("b", LocalDate(2026, 9, 29), LocalDate(2026, 10, 12), "25", "mg")),
    )
}
