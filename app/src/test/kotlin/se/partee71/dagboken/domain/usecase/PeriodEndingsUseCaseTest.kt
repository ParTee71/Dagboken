package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.Recept
import java.time.LocalDate

class PeriodEndingsUseCaseTest {

    private lateinit var useCase: PeriodEndingsUseCase

    @Before fun setUp() { useCase = PeriodEndingsUseCase() }

    private val imorgon: LocalDate = LocalDate.of(2026, 5, 10)

    private fun recept(
        id: String = "r1",
        namn: String = "Prednisolon",
        dos: String = "5",
        aktiv: Boolean = true,
        startDatum: String = "2026-05-01",
        slutDatum: String? = null,
        dosperioder: List<Dosperiod> = emptyList(),
    ) = Recept(
        id = id, namn = namn, dos = dos, enhet = "mg",
        tidpunkter = listOf("Morgon"), upprepning = "dagligen",
        dagar = emptyList(), intervalDagar = 2, aktiv = aktiv, skapad = startDatum,
        startDatum = startDatum, slutDatum = slutDatum, dosperioder = dosperioder,
    )

    @Test fun `recept whose period ends that day is reported`() {
        val result = useCase.endingOn(listOf(recept(slutDatum = imorgon.toString())), imorgon)
        assertEquals(listOf(PeriodSlut.ReceptSlut("Prednisolon")), result)
    }

    @Test fun `recept ending on another day is not reported`() {
        val r = recept(slutDatum = imorgon.plusDays(1).toString())
        assertTrue(useCase.endingOn(listOf(r), imorgon).isEmpty())
    }

    @Test fun `recept without slutDatum is never reported`() {
        assertTrue(useCase.endingOn(listOf(recept()), imorgon).isEmpty())
    }

    @Test fun `inactive recept is ignored`() {
        val r = recept(aktiv = false, slutDatum = imorgon.toString())
        assertTrue(useCase.endingOn(listOf(r), imorgon).isEmpty())
    }

    @Test fun `dosperiod ending that day reports the dose that takes over`() {
        val r = recept(
            dos         = "5",
            slutDatum   = "2026-05-20",
            dosperioder = listOf(Dosperiod("d1", "2026-05-01", imorgon.toString(), "10", "mg")),
        )
        assertEquals(listOf(PeriodSlut.DosperiodSlut("Prednisolon", "5 mg")), useCase.endingOn(listOf(r), imorgon))
    }

    @Test fun `dosperiod handing over to the next dosperiod reports that dose`() {
        val r = recept(
            dos         = "5",
            slutDatum   = "2026-05-20",
            dosperioder = listOf(
                Dosperiod("d1", "2026-05-01", imorgon.toString(), "10", "mg"),
                Dosperiod("d2", imorgon.plusDays(1).toString(), "2026-05-20", "7.5", "mg"),
            ),
        )
        assertEquals(listOf(PeriodSlut.DosperiodSlut("Prednisolon", "7.5 mg")), useCase.endingOn(listOf(r), imorgon))
    }

    @Test fun `dosperiod ending together with the recept only reports the recept ending`() {
        val r = recept(
            slutDatum   = imorgon.toString(),
            dosperioder = listOf(Dosperiod("d1", "2026-05-01", imorgon.toString(), "10", "mg")),
        )
        assertEquals(listOf(PeriodSlut.ReceptSlut("Prednisolon")), useCase.endingOn(listOf(r), imorgon))
    }

    @Test fun `dosperiod that runs to the end of the recept is not reported on its own`() {
        // Dosperioden slutar samma dag som receptet — receptslutet är det som gäller.
        val r = recept(
            slutDatum   = "2026-05-20",
            dosperioder = listOf(Dosperiod("d1", "2026-05-01", null, "10", "mg")),
        )
        assertTrue(useCase.endingOn(listOf(r), imorgon).isEmpty())
    }

    @Test fun `several recept ending the same day are all reported`() {
        val a = recept(id = "r1", namn = "Prednisolon", slutDatum = imorgon.toString())
        val b = recept(id = "r2", namn = "Amoxicillin", slutDatum = imorgon.toString())
        assertEquals(2, useCase.endingOn(listOf(a, b), imorgon).size)
    }
}
