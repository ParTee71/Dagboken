package se.partee71.dagboken.ui.today

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.assertTrue
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
import org.robolectric.annotation.Config
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.DayComparison
import se.partee71.dagboken.core.engine.DayPart
import se.partee71.dagboken.core.engine.DayProgress
import se.partee71.dagboken.core.engine.EnergyTrend
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.OngoingIllness
import se.partee71.dagboken.core.engine.WeekSummary
import se.partee71.dagboken.core.engine.asNeededChoices
import se.partee71.dagboken.core.engine.doseChecklist
import se.partee71.dagboken.core.engine.occasionStates
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.log.CooldownPrompt
import se.partee71.dagboken.ui.SampleMedicines
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorSheetState
import se.partee71.dagboken.ui.log.LogEvent
import se.partee71.dagboken.ui.log.ScreeningSheetInfo
import se.partee71.dagboken.ui.log.ScreeningSheetView
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.common.toMessage
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

    /** En loggad vid behov-dos – visas tagen men växlas inte (FAV-2). */
    private val prn = Dose("p", today, Slot.AS_NEEDED, "Ipren", "400", "mg", DoseStatus.TAKEN, takenAt = at(9, 0), prnId = "9")

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
        rule.onNodeWithText("Visa idag för att logga – eller Logga i efterhand i medicinens meny.").performScrollTo().assertIsDisplayed()
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

        rule.onNodeWithText("Visa idag för att logga – eller Logga i efterhand i medicinens meny.").performClick()
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
        show(content(doses + prn), onEvent = { events += it })
        rule.onNodeWithText("Ipren 400 mg").performScrollTo().assertIsOn().assertIsNotEnabled().performClick()
        assertEquals(emptyList(), events)
    }

    @Test
    fun `en loggad vid behov-dos raderas från radens meny efter bekräftelse (MED-3)`() {
        val events = mutableListOf<TodayEvent>()
        show(content(doses + prn), onEvent = { events += it })
        // Raden växlar inte (en loggad dos bockas inte ur), men långtrycket öppnar menyn (NFR-17).
        rule.onNodeWithText("Ipren 400 mg").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithText("Hoppa över").assertDoesNotExist()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Dosen Ipren 400 mg, 4 okt kl. 09:00 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
        assertEquals(emptyList(), events, "inget raderas utan bekräftelse")
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<TodayEvent>(TodayEvent.Delete(prn)), events)
    }

    @Test
    fun `en engångsdos med tidpunkt raderas, och en migrerad receptdos säger att den hoppas över (MED-3, MED-15)`() {
        val events = mutableListOf<TodayEvent>()
        val oneOff = Dose("o", today, Slot.EVENING, "Melatonin", "3", "mg", DoseStatus.TAKEN, LocalTime(19, 0), takenAt = at(9, 30))
        val migrated = Dose("recept_atarax_2026-10-04_Vid behov", today, Slot.AS_NEEDED, "Atarax", "25", "mg", DoseStatus.TAKEN, takenAt = at(9, 45))
        show(content(doses + oneOff + migrated), onEvent = { events += it })
        rule.onNodeWithText("Melatonin 3 mg").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithText("Hoppa över").assertDoesNotExist()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Dosen Melatonin 3 mg, 4 okt kl. 09:30 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").performClick()
        rule.onNodeWithText("Atarax 25 mg").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Dosen Atarax 25 mg, 4 okt kl. 09:45 markeras som överhoppad och försvinner ur Dagbok. Receptet och anteckningen står kvar.").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<TodayEvent>(TodayEvent.Delete(migrated)), events)
    }

    @Test
    fun `Logga i efterhand i långtrycksmenyn öppnar dosformuläret mot den visade dagen (FAV-10, HEM-11, MED-16)`() {
        val later = mutableListOf<Pair<String, LocalDate>>()
        val past = LocalDate(2026, 10, 2)
        rule.setContent { DagbokenTheme { TodayScreen(DetailUiState.Content(content(date = past)), {}, {}, onLogLater = { id, date -> later += id to date }) } }
        rule.onNodeWithText("Imigran 50 mg").performScrollTo().performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("Logga i efterhand").performClick()
        assertEquals(listOf("2" to past), later)
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
    fun `ett läsfel visar Försök igen`() {
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

    // ── Mående, Din vecka, pågående sjukdom och 7-dagarstrenden (HEM-4, HEM-5, HEM-7, HEM-12, HEM-13, TRD-5) ──

    /** Frukost loggad, lunch 10:00 försenad, kvällsmat 12:30 snart och läggdags kommande. */
    private val reminders = ReminderSettings(
        screeningOccasions = listOf(
            OccasionReminder(Occasion.BREAKFAST, true, LocalTime(8, 0)),
            OccasionReminder(Occasion.LUNCH, true, LocalTime(10, 0)),
            OccasionReminder(Occasion.DINNER, true, LocalTime(12, 30)),
            OccasionReminder(Occasion.BEDTIME, true, LocalTime(21, 0)),
        ),
    )
    private val breakfast = Screening("s1", today, LocalTime(8, 12), Occasion.BREAKFAST, energy = 7, stress = 3)
    private val episode = IllnessEpisode("e", "Förkylning", start = LocalDate(2026, 10, 1))
    private val symptoms = listOf(
        Option("h", OptionKind.SYMPTOM, "Huvudvärk", sortOrder = 0),
        Option("t", OptionKind.SYMPTOM, "Trötthet", sortOrder = 1),
        Option(OTHER_SYMPTOM_ID, OptionKind.SYMPTOM, "Övrigt", sortOrder = 2),
    )

    private fun full(date: LocalDate = today, base: TodayContent = content(date = date, showDone = false)) = base.copy(
        occasions = occasionStates(reminders, listOf(breakfast), date, now, zone),
        weekSummary = WeekSummary(EnergyTrend.UP, 86).takeIf { date == today },
        energyDays = (28..30).map { LocalDate(2026, 9, it) } + (1..4).map { LocalDate(2026, 10, it) },
        energy = listOf(5.5f, 6f, null, 7f, 6.5f, 5f, 7f),
        illness = OngoingIllness(episode, 4, Checkin("c", LocalDate(2026, 10, 3), LocalTime(20, 0), severity = 4)),
    )

    private fun showFull(
        content: TodayContent = full(),
        screening: EditorSheetState<Screening, ScreeningSheetInfo>? = null,
        onScreening: (LogEvent) -> Unit = {},
        onOpenTrends: () -> Unit = {},
    ) {
        rule.setContent { DagbokenTheme { TodayWithSheet(DetailUiState.Content(content), screening, onScreening = onScreening, onOpenTrends = onOpenTrends) } }
    }

    /**
     * Idag med måendearket ovanpå, som i appen: Idag skickar [onScreening] (`LogEvent`) till plusknappens `LogViewModel`, och
     * arket visas av `LogSheets` ovanpå flikarna – här direkt med samma `ScreeningSheetView`.
     */
    @Composable
    private fun TodayWithSheet(
        state: DetailUiState<TodayContent>,
        sheet: EditorSheetState<Screening, ScreeningSheetInfo>?,
        symptomOptions: List<Option> = symptoms,
        onScreening: (LogEvent) -> Unit = {},
        onOpenTrends: () -> Unit = {},
    ) {
        TodayScreen(state, {}, {}, onOpenTrends = onOpenTrends, onScreening = onScreening)
        sheet?.let { key(it) { ScreeningSheetView(it, symptomOptions, onScreening) } }
    }

    @Test
    fun `Mående visar tillfällena med status, och raden eller Logga nu öppnar arket (HEM-4, HEM-5, NFR-17)`() {
        val events = mutableListOf<LogEvent>()
        showFull(onScreening = { events += it })
        rule.onNodeWithText("1 av 4").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Loggad 08:12").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Energi 7").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Stress 3").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Kommande · 21:00").performScrollTo().assertIsDisplayed()

        rule.onAllNodesWithText("Logga nu")[0].performScrollTo().performClick()
        assertEquals(LogEvent.LogScreening(Occasion.LUNCH, null, LocalTime(10, 0)), events.last())
        rule.onNodeWithText("Läggdags").performClick()
        assertEquals(LogEvent.LogScreening(Occasion.BEDTIME, null, LocalTime(21, 0)), events.last())
        rule.onNodeWithText("Efter frukost").performScrollTo().performClick()
        assertEquals(LogEvent.EditScreening(breakfast), events.last())
    }

    @Test
    fun `en tidigare dag är ej loggad utan Försenat, och Logga nu loggar ändå (HEM-4, SCR-6)`() {
        val events = mutableListOf<LogEvent>()
        val past = LocalDate(2026, 10, 2)
        showFull(full(past), onScreening = { events += it })
        rule.onNodeWithText("Mående").performScrollTo()
        assertEquals(4, rule.onAllNodesWithText("Ej loggad").fetchSemanticsNodes().size)
        rule.onNodeWithText("Din vecka").assertDoesNotExist()
        rule.onAllNodesWithText("Logga nu")[1].performScrollTo().performClick()
        assertEquals(LogEvent.LogScreening(Occasion.LUNCH, past, LocalTime(10, 0)), events.last(), "den visade dagen och påminnelsens tid")
    }

    @Test
    fun `Din vecka, pågående sjukdom utan Checka in och trenden med länk till Trender (HEM-7, HEM-12, HEM-13, TRD-5)`() {
        var trends = 0
        showFull(onOpenTrends = { trends++ })
        rule.onNodeWithText("Din vecka").assertIsDisplayed()
        rule.onNodeWithText("Uppåt").assertIsDisplayed()
        rule.onNodeWithText("86 %").assertIsDisplayed()
        rule.onNodeWithText("Förkylning").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Dag 4").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Senaste incheckning 3 okt").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Checka in").assertDoesNotExist()
        rule.onNodeWithText("Visa i Trender").performScrollTo().performClick()
        assertEquals(1, trends)
    }

    @Test
    fun `pågående sjukdom öppnar sjukdomsdetaljen med den visade dagen (HEM-12, HEM-14, SJ-13)`() {
        val opened = mutableListOf<Pair<String, LocalDate>>()
        rule.setContent { DagbokenTheme { TodayScreen(DetailUiState.Content(full()), {}, {}, onOpenIllness = { id, date -> opened += id to date }) } }
        rule.onNodeWithText("Förkylning").performScrollTo().performClick()
        assertEquals(listOf("e" to today), opened)
    }

    @Test
    fun `sjukdomskortet utan incheckning och med en odaterad senaste incheckning (HEM-12)`() {
        var content by mutableStateOf(full().copy(illness = OngoingIllness(episode, 4, null)))
        rule.setContent { DagbokenTheme { TodayScreen(DetailUiState.Content(content), {}, {}) } }
        rule.onNodeWithText("Ingen incheckning än").performScrollTo().assertIsDisplayed()
        content = full().copy(illness = OngoingIllness(episode, 4, Checkin("c", severity = 3)))
        rule.onNodeWithText("Senaste incheckningen saknar datum").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ingen incheckning än").assertDoesNotExist()
    }

    @Test
    fun `utan aktiverade tillfällen, sjukdom eller underlag för veckan visas inga sådana kort (HEM-12, HEM-13)`() {
        show()
        rule.onNodeWithText("Mående").assertDoesNotExist()
        rule.onNodeWithText("Pågående sjukdom").assertDoesNotExist()
        rule.onNodeWithText("Din vecka").assertDoesNotExist()
    }

    @Test
    fun `arket går i steg och sparar – en tidigare dag står dagen i rubriken (HEM-5, SCR-1, SCR-2)`() {
        val events = mutableListOf<LogEvent>()
        val past = LocalDate(2026, 10, 2)
        val new = Screening("ny", past, LocalTime(12, 0), Occasion.LUNCH)
        val sheet = sheet(null, new)
        showFull(full(past), screening = sheet, onScreening = { events += it })
        rule.onNodeWithText("Lunch · fre 2 okt 2026").assertIsDisplayed()
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Steg 2 av 3").assertIsDisplayed()
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Övrigt").performClick()
        assertEquals(LogEvent.ChangeSymptoms(listOf(SymptomScore(OTHER_SYMPTOM_ID, 1))), events.last())
        rule.onNodeWithText("Spara").performClick()
        assertEquals(LogEvent.SaveScreening, events.last())
    }

    /** Ett ark som `EditorSheet` öppnar det: den delade `EditorState` med [value], [loaded] = den sparade loggen. */
    private fun sheet(loaded: Screening?, value: Screening) = EditorSheetState(
        EditorState(value, Validator { emptyMap() }, saveUnchanged = loaded == null),
        loaded,
        ScreeningSheetInfo(value.occasion, value.date?.takeIf { it != today }),
        CoroutineScope(Dispatchers.Main.immediate),
    )

    @Test
    fun `symptomvalen följer listan medan arket är öppet – en sen lista ger symptomsteget (SCR-2)`() {
        val sheet = sheet(null, Screening("ny", today, LocalTime(10, 30), Occasion.LUNCH))
        var options by mutableStateOf(emptyList<Option>())
        rule.setContent { DagbokenTheme { TodayWithSheet(DetailUiState.Content(full()), sheet, options) } }
        rule.onNodeWithText("Steg 1 av 2").assertIsDisplayed()
        options = symptoms
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
    }

    @Test
    fun `sparat – arket döljs och stängs sedan (SCR-1)`() {
        val events = mutableListOf<LogEvent>()
        val sheet = sheet(null, Screening("ny", today, LocalTime(10, 30), Occasion.LUNCH))
        showFull(screening = sheet, onScreening = { events += it })
        rule.runOnIdle { sheet.saved() }
        rule.waitForIdle()
        assertEquals(LogEvent.CloseScreening, events.last())
    }

    @Test
    fun `arket står sig när skärmen bakom laddar om eller visar fel – samma rubrik och tre steg (HEM-5)`() {
        val past = LocalDate(2026, 10, 2)
        val sheet = sheet(null, Screening("ny", past, LocalTime(12, 0), Occasion.LUNCH))
        var state by mutableStateOf<DetailUiState<TodayContent>>(DetailUiState.Content(full(past)))
        rule.setContent { DagbokenTheme { TodayWithSheet(state, sheet) } }
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
        for (next in listOf(DetailUiState.Loading, DetailUiState.Error(DataError.Offline))) {
            state = next
            rule.onNodeWithText("Lunch · fre 2 okt 2026").assertIsDisplayed()
            rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
        }
    }

    @Test
    fun `en ny logg vars sparning misslyckades frågar Släng ändringar vid bakåt (NFR-10)`() {
        val events = mutableListOf<LogEvent>()
        val sheet = sheet(null, Screening("ny", today, LocalTime(10, 30), Occasion.LUNCH))
        sheet.showError(Failure(DataError.Offline))
        showFull(screening = sheet, onScreening = { events += it })
        Espresso.pressBack()
        rule.onNodeWithText("Släng ändringar?").assertIsDisplayed()
        assertTrue(events.none { it == LogEvent.CloseScreening })
    }

    @Test
    fun `bakåt med osparade ändringar frågar Släng ändringar och arket står kvar tills svaret (NFR-10)`() {
        val events = mutableListOf<LogEvent>()
        val sheet = sheet(breakfast, breakfast)
        showFull(screening = sheet, onScreening = { events += it })
        sheet.editor.update { it.copy(energy = 3) }
        rule.waitForIdle()
        Espresso.pressBack()
        rule.onNodeWithText("Släng ändringar?").assertIsDisplayed()
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
        assertTrue(events.none { it == LogEvent.CloseScreening })
        rule.onNodeWithText("Fortsätt redigera").performClick()
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()

        Espresso.pressBack()
        rule.onNodeWithText("Släng").performClick()
        // Arket döljs först (animerat), sedan stängs det.
        rule.waitForIdle()
        assertEquals(LogEvent.CloseScreening, events.last())
    }

    @Test
    fun `utan ändringar stänger bakåt arket direkt`() {
        val events = mutableListOf<LogEvent>()
        showFull(screening = sheet(breakfast, breakfast), onScreening = { events += it })
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
        assertEquals(LogEvent.CloseScreening, events.last())
    }

    @Test
    fun `under sparningen stänger bakåt inte arket (SCR-1)`() {
        val events = mutableListOf<LogEvent>()
        val sheet = sheet(breakfast, breakfast)
        val gate = CompletableDeferred<Result<Unit>>()
        showFull(screening = sheet, onScreening = { events += it })
        sheet.editor.update { it.copy(stress = 6) }
        val saving = CoroutineScope(Dispatchers.Main.immediate).launch { sheet.editor.save { gate.await() } }
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Släng ändringar?").assertDoesNotExist()
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
        assertTrue(events.none { it == LogEvent.CloseScreening })
        gate.complete(Result.success(Unit))
        rule.waitForIdle()
        assertTrue(saving.isCompleted)
    }

    @Test
    fun `en oförändrad logg kan inte sparas, och ett sparfel visas i arket (SCR-1)`() {
        showFull(screening = sheet(breakfast, breakfast).also { it.showError(Failure(DataError.PermissionDenied)) })
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Spara").assertIsNotEnabled()
        rule.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(DataError.PermissionDenied.toMessage())).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h1800dp-xxhdpi")
    fun `skärmdump - alla kort`() = rule.captureLightAndDark("Today_alla_kort") {
        TodayScreen(DetailUiState.Content(full()), {}, {})
    }

    private val withSymptoms = sheet(null, Screening("ny", today, LocalTime(10, 30), Occasion.LUNCH, energy = 6, stress = 4, symptoms = listOf(SymptomScore("h", 4), SymptomScore(OTHER_SYMPTOM_ID, 2, "Ont i knät"))))

    /** Ett ark per test – skapat en gång, inte vid varje komposition (arket har nyckeln `key(sheet)`). */
    private val newLunch = sheet(null, Screening("ny", today, LocalTime(10, 30), Occasion.LUNCH, energy = 6, stress = 4))

    @Test
    fun `skärmdump - arket steg 1`() = rule.captureScreenLightAndDark("Today_maende_steg1") {
        TodayWithSheet(DetailUiState.Content(full()), newLunch)
    }

    @Test
    fun `skärmdump - arket steg 2`() = rule.captureScreenLightAndDark("Today_maende_steg2", open = { onNodeWithText("Nästa").clickWithoutRipple() }) {
        TodayWithSheet(DetailUiState.Content(full()), newLunch)
    }

    @Test
    fun `skärmdump - arket steg 3`() = rule.captureScreenLightAndDark(
        "Today_maende_steg3",
        open = {
            onNodeWithText("Nästa").clickWithoutRipple()
            waitForIdle()
            onNodeWithText("Nästa").clickWithoutRipple()
        },
    ) {
        TodayWithSheet(DetailUiState.Content(full()), withSymptoms)
    }

    private companion object {
        const val FONT_SCALE = 1.3f

        /** Längre än en kort snackbar. */
        const val SNACKBAR_MILLIS = 10_000L
    }
}
