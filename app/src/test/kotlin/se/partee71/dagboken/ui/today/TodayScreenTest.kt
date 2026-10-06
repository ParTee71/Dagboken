package se.partee71.dagboken.ui.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DayComparison
import se.partee71.dagboken.core.engine.DayPart
import se.partee71.dagboken.core.engine.DayProgress
import se.partee71.dagboken.core.engine.asNeededChoices
import se.partee71.dagboken.core.engine.doseChecklist
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.SampleMedicines
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Fliken Idag: det unika beteendet (MED-2, MED-3, MED-5, MED-13, FAV-2, FAV-4, FAV-11, HEM-11, HEM-19) och
 * skärmdumpar ljust + mörkt bredvid tavlorna "I · Papper och teal" och "Belöning: dagen klar" (NFR-20) – vanligt
 * läge, belöningsläge och stor text. Skärmen använder ingen ram, så inget ramkontrakt gäller. Påhittad data:
 * söndag 4 oktober 2026 kl. 10:30.
 */
@RunWith(RobolectricTestRunner::class)
class TodayScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 4)
    private val now = at(10, 30)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private val sertralin = SampleMedicines.sertralin
    private val levaxin = SampleMedicines.levaxin
    private val alvedon = PrnMedicine("1", "Alvedon", "500", "mg", favorite = true, note = "Inte på fastande mage.")
    private val imigran = PrnMedicine("2", "Imigran", "50", "mg", favorite = true)
    private val loratadin = PrnMedicine("3", "Loratadin", "10", "mg")
    private val betapred = Prescription("b", "Betapred", "0,5", "mg", listOf(Slot.MORNING), Schedule.Repeating(), Period(LocalDate(2026, 9, 1)))

    private fun dose(id: String, name: String, dose: String, unit: String, slot: Slot, status: DoseStatus = DoseStatus.PLANNED, takenAt: Instant? = null, prescriptionId: String? = null) =
        Dose(id, today, slot, name, dose, unit, status, slot.defaultTime, takenAt = takenAt, prescriptionId = prescriptionId)

    private val levaxinDose = dose("d1", "Levaxin", "100", "µg", Slot.MORNING, DoseStatus.TAKEN, at(7, 12), "l")
    private val sertralinDose = dose("d2", "Sertralin", "75", "mg", Slot.MORNING, DoseStatus.TAKEN, at(7, 15), "s")
    private val vitaminDose = dose("d3", "D-vitamin", "20", "µg", Slot.MIDMORNING, prescriptionId = "v")
    private val omegaDose = dose("d4", "Omega-3", "1", "kapsel", Slot.LUNCH, prescriptionId = "o").copy(note = "Tas med mat.")
    private val upcoming = listOf(
        dose("d5", "Metformin", "500", "mg", Slot.EVENING, prescriptionId = "m"),
        dose("d6", "Melatonin", "2", "mg", Slot.NIGHT, prescriptionId = "n"),
    )
    private val doses = listOf(levaxinDose, sertralinDose, vitaminDose, omegaDose) + upcoming

    private fun content(
        doses: List<Dose> = this.doses,
        date: LocalDate = today,
        progress: DayProgress = DayProgress(4, 9),
        showDone: Boolean = true,
        dayDone: DayDone? = null,
    ) = TodayContent(
        today = today,
        date = date,
        week = LocalDate(2026, 9, 28),
        dayPart = DayPart.MORNING,
        datesWithEntries = setOf(LocalDate(2026, 9, 29), LocalDate(2026, 10, 1), LocalDate(2026, 10, 2), today),
        progress = progress,
        todayComplete = dayDone != null,
        doseProgress = DayProgress(doses.count { it.status != DoseStatus.PLANNED }, doses.size),
        checklist = doseChecklist(doses, date, now, zone),
        showDone = showDone,
        showUpcoming = false,
        prescriptions = listOf(sertralin, levaxin).associateBy { it.id },
        choices = asNeededChoices(listOf(alvedon, imigran, loratadin), listOf(levaxin, betapred), today),
        dayDone = dayDone,
        zone = zone,
    )

    private val allTaken = doses.map { it.copy(status = DoseStatus.TAKEN, takenAt = it.takenAt ?: at(10, 0)) }
    private val celebrating = content(allTaken, progress = DayProgress(9, 9), showDone = false, dayDone = DayDone(DayComparison(6.5f, 1.3f), streak = 4, playConfetti = false))

    private fun show(
        content: TodayContent = content(),
        cooldown: CooldownPrompt? = null,
        notice: TodayNotice? = null,
        onEvent: (TodayEvent) -> Unit = {},
        onEditPrn: (String) -> Unit = {},
    ) {
        rule.setContent { DagbokenTheme { TodayScreen(DetailUiState.Content(content), onEvent, onEditPrn, cooldown = cooldown, notice = notice) } }
    }

    @Test
    fun `rubriken är hälsningen med dagen och veckan, och raderna visar tid, höjning och status (HEM-1, HEM-2, REC-12, MED-13)`() {
        show()
        rule.onNodeWithText("God morgon").assertIsDisplayed()
        rule.onNodeWithText("Söndag 4 oktober · vecka 40").assertIsDisplayed()
        rule.onNodeWithText("Morgon · tagen 07:15 · 50 mg + 25 mg höjning t.o.m. 12 okt").assertIsDisplayed()
        rule.onNodeWithText("Förmiddag · 10:00").assertIsDisplayed()
        rule.onNodeWithText("Försenat").assertIsDisplayed()
        rule.onNodeWithText("Snart").assertIsDisplayed()
        rule.onNodeWithText("Visa kommande (2)").assertIsDisplayed()
        rule.onNodeWithText("Dölj tagna").assertIsDisplayed()
        rule.onNodeWithText("2 av 6").assertIsDisplayed()
    }

    @Test
    fun `hela raden växlar tagen, och Hoppa över bekräftas i en dialog (MED-2, MED-3, NFR-17)`() {
        val events = mutableListOf<TodayEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Levaxin 100 µg").assertIsOn()
        rule.onNodeWithText("D-vitamin 20 µg").performClick()
        assertEquals(TodayEvent.SetTaken(vitaminDose, true), events.last())
        rule.onNodeWithText("Levaxin 100 µg").performClick()
        assertEquals(TodayEvent.SetTaken(levaxinDose, false), events.last())

        rule.onNodeWithText("D-vitamin 20 µg").performTouchInput { longClick() }
        rule.onNodeWithText("Hoppa över").performClick()
        rule.onNodeWithText("Hoppa över dos?").assertIsDisplayed()
        rule.onNodeWithText("\"D-vitamin 20 µg\" markeras som hoppad för idag.").assertIsDisplayed()
        rule.onNodeWithText("Hoppa över").performClick()
        assertEquals(TodayEvent.Skip(vitaminDose), events.last())
    }

    @Test
    fun `knapparna visar och döljer tagna och kommande (MED-5, MED-13)`() {
        val events = mutableListOf<TodayEvent>()
        show(content(showDone = false), onEvent = { events += it })
        rule.onNodeWithText("Levaxin 100 µg").assertDoesNotExist()
        rule.onNodeWithText("Visa tagna (2)").performClick()
        rule.onNodeWithText("Visa kommande (2)").performClick()
        assertEquals(listOf<TodayEvent>(TodayEvent.ToggleDone, TodayEvent.ToggleUpcoming), events)
    }

    @Test
    fun `snabbvalet loggar, långtryck ger Redigera, Favorit, anteckning och Radera (FAV-2, FAV-3, FAV-8, FAV-9, HEM-11)`() {
        val events = mutableListOf<TodayEvent>()
        val edited = mutableListOf<String>()
        show(onEvent = { events += it }, onEditPrn = { edited += it })
        rule.onNodeWithText("Imigran 50 mg").performScrollTo().performClick()
        assertEquals(TodayEvent.LogAsNeeded(imigran), events.last())

        rule.onNodeWithText("Imigran 50 mg").performTouchInput { longClick() }
        assertEquals(1, events.size, "långtrycket loggar inget")
        rule.onNodeWithText("Redigera").performClick()
        assertEquals(listOf("2"), edited)

        rule.onNodeWithText("Alvedon 500 mg").performTouchInput { longClick() }
        rule.onNodeWithText("Ta bort Alvedon som favorit").performClick()
        assertEquals(TodayEvent.ToggleFavorite(alvedon), events.last())

        rule.onNodeWithText("Alvedon 500 mg").performTouchInput { longClick() }
        rule.onNodeWithText("Visa anteckning").performClick()
        rule.onNodeWithText("Inte på fastande mage.").assertIsDisplayed()
        rule.onNodeWithText("Stäng").performClick()

        rule.onNodeWithText("Alvedon 500 mg").performTouchInput { longClick() }
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Radera Alvedon?").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(TodayEvent.DeleteMedicine(alvedon), events.last())
    }

    @Test
    fun `Fler listar övriga vid behov-mediciner och recepten under Recept (FAV-2, FAV-11)`() {
        val events = mutableListOf<TodayEvent>()
        show(onEvent = { events += it })
        rule.onNodeWithText("Fler (3)").performScrollTo().performClick()
        rule.onNodeWithText("RECEPT").assertIsDisplayed()
        rule.onNodeWithText("Loratadin 10 mg").performClick()
        assertEquals(TodayEvent.LogAsNeeded(loratadin), events.last())
        rule.onNodeWithText("Fler (3)").performScrollTo().performClick()
        rule.onNodeWithText("Betapred 0,5 mg").performClick()
        assertEquals(TodayEvent.LogExtra(betapred), events.last())
    }

    @Test
    fun `För tidigt visar kvarvarande tid och Ta ändå (FAV-4)`() {
        val events = mutableListOf<TodayEvent>()
        show(cooldown = CooldownPrompt(alvedon, 90.minutes + 20.minutes), onEvent = { events += it })
        rule.onNodeWithText("För tidigt").assertIsDisplayed()
        rule.onNodeWithText("Du bör vänta 1h 50m till för Alvedon. Vill du ta ändå?").assertIsDisplayed()
        rule.onNodeWithText("Ta ändå").performClick()
        assertEquals(TodayEvent.ConfirmCooldown, events.last())
    }

    @Test
    fun `ett snabbval bekräftas med ett meddelande (FAV-2, FAV-6)`() {
        val events = mutableListOf<TodayEvent>()
        show(notice = TodayNotice(R.string.today_limit_reached_format, 2, "Imigran"), onEvent = { events += it })
        rule.onNodeWithText("Högst 2 doser per dag – gränsen är nådd för Imigran.").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(SNACKBAR_MILLIS)
        rule.waitForIdle()
        assertEquals(listOf<TodayEvent>(TodayEvent.NoticeShown), events)
    }

    @Test
    fun `datumremsan väljer en dag och en tidigare dag har ingen belöning (HEM-14, HEM-19)`() {
        val events = mutableListOf<TodayEvent>()
        show(content(allTaken, date = LocalDate(2026, 10, 2), progress = DayProgress(9, 9)), onEvent = { events += it })
        rule.onNodeWithText("God morgon").assertIsDisplayed()
        rule.onNodeWithContentDescription("9 av 9 klara").assertIsDisplayed()
        rule.onNodeWithContentDescription("tors 1 okt 2026, har poster").performClick()
        assertEquals(TodayEvent.SelectDate(LocalDate(2026, 10, 1)), events.last())
    }

    @Test
    fun `belöningsläget byter rubrik och visar sammanfattningen (HEM-19)`() {
        show(celebrating)
        rule.onNodeWithText("Alla dagens mediciner är avklarade.").assertIsDisplayed()
        rule.onNodeWithContentDescription("9 av 9 klara, allt klart").assertIsDisplayed()
        rule.onNodeWithText("+1,3").assertIsDisplayed()
        assertEquals(2, rule.onAllNodesWithText("Allt klart för idag").fetchSemanticsNodes().size, "rubriken och kortet")
    }

    @Test
    fun `en tidigare dag har inaktiva snabbval med förklaring, men långtrycksmenyn finns kvar (FAV-2, HEM-11)`() {
        val events = mutableListOf<TodayEvent>()
        val edited = mutableListOf<String>()
        show(content(date = LocalDate(2026, 10, 2)), onEvent = { events += it }, onEditPrn = { edited += it })
        rule.onNodeWithText("Visa idag för att logga – efterhandsloggning kommer med dosformuläret.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Imigran 50 mg").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Loggas bara idag"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        rule.onNodeWithText("Fler (3)").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Loggas bara idag")).performClick()
        rule.onNodeWithText("Loratadin 10 mg").assertDoesNotExist()
        rule.onNodeWithText("Imigran 50 mg").performClick()
        assertEquals(emptyList(), events)

        // TalkBack: långtrycksåtgärden öppnar menyn också på en tidigare dag.
        rule.onNodeWithText("Imigran 50 mg").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("Redigera").performClick()
        assertEquals(listOf("2"), edited)

        rule.onNodeWithText("Visa idag för att logga – efterhandsloggning kommer med dosformuläret.").performClick()
        assertEquals(listOf<TodayEvent>(TodayEvent.SelectDate(today)), events)
    }

    @Test
    fun `allt avklarat visas inte när dolda kommande doser återstår (MED-13)`() {
        val noneShown = doses.filter { it.status == DoseStatus.TAKEN } + upcoming
        show(content(noneShown, showDone = false))
        rule.onNodeWithText("Alla dagens mediciner är avklarade.").assertDoesNotExist()
        rule.onNodeWithText("Visa kommande (2)").assertIsDisplayed()
    }

    @Test
    fun `radens meny följer dosen när raden ovanför försvinner`() {
        val events = mutableListOf<TodayEvent>()
        var current by mutableStateOf(content(showDone = false))
        rule.setContent { DagbokenTheme { TodayScreen(DetailUiState.Content(current), { events += it }, {}) } }
        rule.onNodeWithText("Omega-3 1 kapsel").performTouchInput { longClick() }
        rule.onNodeWithText("Hoppa över").assertIsDisplayed()

        // D-vitaminet ovanför bockas av på en annan enhet och döljs bland de tagna.
        current = content(doses.map { if (it == vitaminDose) it.copy(status = DoseStatus.TAKEN, takenAt = at(10, 20)) else it }, showDone = false)
        rule.onNodeWithText("Hoppa över").performClick()
        rule.onNodeWithText("Hoppa över").performClick()
        assertEquals(TodayEvent.Skip(omegaDose), events.last())
    }

    @Test
    fun `alla återstående doser dolda långt fram – kortet säger när nästa dos är (MED-13)`() {
        show(content(upcoming, showDone = false))
        rule.onNodeWithText("Inget att ta just nu").assertIsDisplayed()
        rule.onNodeWithText("Nästa dos kl. 19:00.").assertIsDisplayed()
    }

    @Test
    fun `en loggad vid behov-dos visas tagen men växlas inte (FAV-2)`() {
        val events = mutableListOf<TodayEvent>()
        val prn = Dose("p", today, Slot.AS_NEEDED, "Ipren", "400", "mg", DoseStatus.TAKEN, takenAt = at(9, 0), prnId = "9")
        show(content(doses + prn), onEvent = { events += it })
        rule.onNodeWithText("Ipren 400 mg").performScrollTo().assertIsOn().assertIsNotEnabled().performClick()
        assertEquals(emptyList(), events)
    }

    @Test
    fun `Hoppa över på en tidigare dag nämner dagen (MED-3)`() {
        val past = LocalDate(2026, 10, 2)
        show(content(listOf(vitaminDose.copy(date = past)), date = past))
        rule.onNodeWithText("D-vitamin 20 µg").performTouchInput { longClick() }
        rule.onNodeWithText("Hoppa över").performClick()
        rule.onNodeWithText("\"D-vitamin 20 µg\" markeras som hoppad för fre 2 okt 2026.").assertIsDisplayed()
    }

    @Test
    fun `ett läsfel visar Försök igen (NFR-5)`() {
        val events = mutableListOf<TodayEvent>()
        rule.setContent { DagbokenTheme { TodayScreen(DetailUiState.Error(DataError.Offline), { events += it }, {}) } }
        rule.onNodeWithText("Försök igen").performClick()
        assertEquals(listOf<TodayEvent>(TodayEvent.Retry), events)
    }

    @Test
    fun `skärmdump - vanligt läge`() = rule.captureLightAndDark("Today_vanligt") {
        TodayScreen(DetailUiState.Content(content()), {}, {})
    }

    @Test
    fun `skärmdump - belöningsläge`() = rule.captureLightAndDark("Today_klar") {
        TodayScreen(DetailUiState.Content(celebrating), {}, {})
    }

    @Test
    fun `skärmdump - stor text`() = rule.captureLightAndDark("Today_fontScale13") {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = FONT_SCALE)) {
            TodayScreen(DetailUiState.Content(content()), {}, {})
        }
    }

    private companion object {
        const val FONT_SCALE = 1.3f

        /** Längre än en kort snackbar. */
        const val SNACKBAR_MILLIS = 10_000L
    }
}
