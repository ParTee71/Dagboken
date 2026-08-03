package se.partee71.dagboken.ui.mediciner.add

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.NoteTarget
import se.partee71.dagboken.domain.model.Recept
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditReceptViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var viewModel: AddEditReceptViewModel

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        noteRepo = mockk(relaxed = true) {
            every { observe(any(), any()) } returns flowOf("")
        }
        viewModel = AddEditReceptViewModel(repo, noteRepo)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun recept(id: String = "r1") = Recept(
        id = id, namn = "Metformin", dos = "500", enhet = "mg",
        tidpunkter = listOf("Morgon"), upprepning = "dagligen",
        dagar = emptyList(), intervalDagar = 2, aktiv = true, skapad = "2026-01-01",
    )

    @Test fun `save persists the entered recept`() = runTest {
        viewModel.updateForm { copy(namn = "Metformin", dos = "500") }

        viewModel.save()

        val saved = slot<Recept>()
        coVerify { repo.saveRecept(capture(saved)) }
        assertEquals("Metformin", saved.captured.namn)
    }

    // ─── anteckning ───────────────────────────────────────────────────────────

    @Test fun `loadForEdit populates form note from noteRepo`() = runTest {
        coEvery { repo.getReceptById("r1") } returns recept()
        every { noteRepo.observe(NoteTarget.RECEPT, "r1") } returns flowOf("Ta med mat")

        viewModel.loadForEdit("r1")

        assertEquals("Ta med mat", viewModel.form.value.anteckning)
    }

    @Test fun `save persists the note under RECEPT target`() = runTest {
        viewModel.updateForm { copy(namn = "Metformin", dos = "500", anteckning = "Ta med mat") }

        viewModel.save()

        coVerify { noteRepo.save(NoteTarget.RECEPT, any(), "Ta med mat") }
    }

    // ─── isDirty ──────────────────────────────────────────────────────────────

    @Test fun `isDirty is false on a fresh form`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty becomes true after a field changes`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(namn = "Metformin") }
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false right after loadForEdit`() = runTest {
        coEvery { repo.getReceptById("r1") } returns recept()
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.loadForEdit("r1")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `isDirty is false again after save`() = runTest {
        viewModel.isDirty.test {
            assertEquals(false, awaitItem())
            viewModel.updateForm { copy(namn = "Metformin", dos = "500") }
            assertEquals(true, awaitItem())
            viewModel.save()
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── period (REC-7) ───────────────────────────────────────────────────────

    @Test fun `tills vidare saves without slutDatum`() = runTest {
        viewModel.updateForm { copy(namn = "Metformin", dos = "500", periodMode = PeriodMode.TILLS_VIDARE) }

        viewModel.save()

        val saved = slot<Recept>()
        coVerify { repo.saveRecept(capture(saved)) }
        assertNull(saved.captured.slutDatum)
    }

    @Test fun `length in days resolves to an inclusive slutDatum`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Amoxicillin", dos = "500",
                startDatum = "2026-05-01", periodMode = PeriodMode.LANGD, langdDagar = 10,
            )
        }

        viewModel.save()

        val saved = slot<Recept>()
        coVerify { repo.saveRecept(capture(saved)) }
        assertEquals("2026-05-01", saved.captured.startDatum)
        assertEquals("2026-05-10", saved.captured.slutDatum)
    }

    @Test fun `two weeks resolves to fourteen inclusive days`() {
        val form = ReceptForm(startDatum = "2026-05-01", periodMode = PeriodMode.LANGD, langdDagar = 14)
        assertEquals("2026-05-14", form.resolvedSlutDatum())
    }

    @Test fun `explicit end date is used as is`() {
        val form = ReceptForm(
            startDatum = "2026-05-01", periodMode = PeriodMode.SLUTDATUM, slutDatumVal = "2026-06-01",
        )
        assertEquals("2026-06-01", form.resolvedSlutDatum())
    }

    @Test fun `loadForEdit derives period fields from the saved recept`() = runTest {
        coEvery { repo.getReceptById("r1") } returns recept().copy(
            startDatum = "2026-05-01", slutDatum = "2026-05-10",
        )

        viewModel.loadForEdit("r1")

        val form = viewModel.form.value
        assertEquals("2026-05-01", form.startDatum)
        assertEquals(PeriodMode.SLUTDATUM, form.periodMode)
        assertEquals("2026-05-10", form.slutDatumVal)
        assertEquals(10, form.langdDagar)
    }

    @Test fun `loadForEdit falls back to skapad when the recept has no startDatum`() = runTest {
        coEvery { repo.getReceptById("r1") } returns recept()

        viewModel.loadForEdit("r1")

        assertEquals("2026-01-01", viewModel.form.value.startDatum)
        assertEquals(PeriodMode.TILLS_VIDARE, viewModel.form.value.periodMode)
    }

    @Test fun `end date before start date is a validation error`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Metformin", dos = "500", startDatum = "2026-05-10",
                periodMode = PeriodMode.SLUTDATUM, slutDatumVal = "2026-05-01",
            )
        }

        assertEquals(ReceptFormError.SLUT_FORE_START, viewModel.validationError.value)
        viewModel.save()
        coVerify(exactly = 0) { repo.saveRecept(any()) }
    }

    // ─── dosperioder (REC-9) ──────────────────────────────────────────────────

    @Test fun `addDosperiod seeds from the base dose`() = runTest {
        viewModel.updateForm { copy(namn = "Prednisolon", dos = "10", enhet = "mg", startDatum = "2026-05-01") }

        viewModel.addDosperiod()

        val p = viewModel.form.value.dosperioder.single()
        assertEquals("10", p.dos)
        assertEquals("mg", p.enhet)
        assertEquals("2026-05-01", p.startDatum)
    }

    @Test fun `a second dosperiod starts after the first one ends`() = runTest {
        viewModel.updateForm { copy(namn = "Prednisolon", dos = "10", startDatum = "2026-05-01") }
        viewModel.addDosperiod()
        val first = viewModel.form.value.dosperioder.single()

        viewModel.addDosperiod()

        val second = viewModel.form.value.dosperioder.last()
        assertEquals(LocalDate.parse(first.slutDatum).plusDays(1).toString(), second.startDatum)
    }

    @Test fun `overlapping dosperioder is a validation error`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Prednisolon", dos = "10",
                dosperioder = listOf(
                    Dosperiod("d1", "2026-05-01", "2026-05-05", "10", "mg"),
                    Dosperiod("d2", "2026-05-05", "2026-05-10", "5", "mg"),
                ),
            )
        }

        assertEquals(ReceptFormError.DOSPERIOD_OVERLAPP, viewModel.validationError.value)
        viewModel.save()
        coVerify(exactly = 0) { repo.saveRecept(any()) }
    }

    @Test fun `back to back dosperioder are valid`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Prednisolon", dos = "10",
                dosperioder = listOf(
                    Dosperiod("d1", "2026-05-01", "2026-05-05", "10", "mg"),
                    Dosperiod("d2", "2026-05-06", "2026-05-10", "5", "mg"),
                ),
            )
        }

        assertNull(viewModel.validationError.value)
    }

    @Test fun `dosperiod ending before it starts is a validation error`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Prednisolon", dos = "10",
                dosperioder = listOf(Dosperiod("d1", "2026-05-10", "2026-05-01", "10", "mg")),
            )
        }

        assertEquals(ReceptFormError.DOSPERIOD_SLUT_FORE_START, viewModel.validationError.value)
    }

    @Test fun `dosperiod without a dose is a validation error`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Prednisolon", dos = "10",
                dosperioder = listOf(Dosperiod("d1", "2026-05-01", "2026-05-05", "", "mg")),
            )
        }

        assertEquals(ReceptFormError.DOSPERIOD_UTAN_DOS, viewModel.validationError.value)
    }

    @Test fun `removeDosperiod drops only that dosperiod`() = runTest {
        viewModel.updateForm {
            copy(
                dosperioder = listOf(
                    Dosperiod("d1", "2026-05-01", "2026-05-05", "10", "mg"),
                    Dosperiod("d2", "2026-05-06", "2026-05-10", "5", "mg"),
                ),
            )
        }

        viewModel.removeDosperiod("d1")

        assertEquals(listOf("d2"), viewModel.form.value.dosperioder.map { it.id })
    }

    @Test fun `save persists dosperioder on the recept`() = runTest {
        viewModel.updateForm {
            copy(
                namn = "Prednisolon", dos = "10",
                dosperioder = listOf(Dosperiod("d1", "2026-05-01", "2026-05-05", " 20 ", "mg")),
            )
        }

        viewModel.save()

        val saved = slot<Recept>()
        coVerify { repo.saveRecept(capture(saved)) }
        assertEquals("20", saved.captured.dosperioder.single().dos)
    }
}
