package se.partee71.dagboken.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.PrnCheck
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseIds
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.runEditScreenContract
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Plusknappens Dos och Sjukdom (MED-11, MED-15, MED-16, FAV-10, SJ-1, SJ-2, SJ-3, SJ-11, NAV-10): ramens kontrakt per
 * formulär, det unika och skärmdumpar bredvid mockupens tavlor (canvas avsnitt 12 – "Ny dos (engångs)", "Vid behov i
 * efterhand – för tidigt", "Redigera tagen receptdos", "Sjukdom: checka in eller ny", "Ny sjukdomsepisod", "Ny
 * incheckning"). Påhittad data.
 */
@RunWith(RobolectricTestRunner::class)
class DoseAndIllnessScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val day = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val now = at(day, 14, 20)
    private val created = Instant.fromEpochSeconds(1_791_270_000)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private val alvedon = PrnMedicine("alvedon", "Alvedon", "500", "mg", minHoursBetween = 4, maxPerDay = 3, favorite = true)
    private val ipren = PrnMedicine("ipren", "Ipren", "400", "mg", minHoursBetween = 6)
    private val oneOff = Dose("n", day, Slot.AS_NEEDED, "Melatonin", "3", "mg", DoseStatus.TAKEN, LocalTime(14, 20), now, createdAt = created)
    private val later = Dose("", yesterday, Slot.AS_NEEDED, "Alvedon", "500", "mg", DoseStatus.TAKEN, LocalTime(21, 30), at(yesterday, 21, 30), prnId = "alvedon", note = "Huvudvärk")
    private val recipe = Dose(
        DoseIds.prescribed("levaxin", yesterday, Slot.MORNING), yesterday, Slot.MORNING, "Levaxin", "100", "µg", DoseStatus.TAKEN,
        LocalTime(7, 0), at(yesterday, 7, 12), prescriptionId = "levaxin", createdAt = created, note = "Med frukost",
    )
    private val flu = IllnessEpisode("flu", "Förkylning", LocalDate(2026, 10, 3), createdAt = created)
    private val symptoms = listOf(
        Option("headache", OptionKind.SYMPTOM, "Huvudvärk", favorite = true),
        Option("throat", OptionKind.SYMPTOM, "Halsont", favorite = true, sortOrder = 1),
        Option("tired", OptionKind.SYMPTOM, "Trötthet", sortOrder = 2),
        Option(OTHER_SYMPTOM_ID, OptionKind.SYMPTOM, "Övrigt", sortOrder = 3),
    )
    private val checkin = Checkin("c1", day, LocalTime(8, 30), severity = 6, symptoms = listOf(SymptomScore("throat", 7), SymptomScore("tired", 5)), createdAt = created)
    private val start = EpisodeStart(IllnessEpisode("e", "Influensa", day, createdAt = created), Checkin("c", day, LocalTime(14, 20), severity = 5, createdAt = created))

    private fun <T> form(value: T, isNew: Boolean = true, stored: T? = null, onEvent: (EntryEditEvent<T>) -> Unit = {}) =
        EntryForm(isNew, EditorUiState(value), emptyFlow(), onEvent, stored)

    // ── Dos ───────────────────────────────────────────────────────────────

    @Test
    fun `engångsdosen uppfyller redigeringskontraktet (MED-11, NFR-10)`() {
        val editor = EditorState(oneOff.copy(name = ""), doseValidator({ now }))
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(DoseField.NAME) { it.copy(name = " ") } },
            makeValid = { update(DoseField.NAME) { it.copy(name = "Melatonin") } },
            invalidMessage = "Ange ett namn",
        ) { state, effects, onSave, onClose ->
            DoseEditScreen(EntryForm(true, state, effects, { if (it == EntryEditEvent.Save) onSave() }), onClose, DoseMode.NEW, zone)
        }
    }

    @Test
    fun `i efterhand uppfyller redigeringskontraktet – en tid i framtiden går inte att spara (MED-16, NFR-10)`() {
        val editor = EditorState(later, doseValidator({ now }))
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(DoseField.TAKEN_AT) { it.copy(takenAt = now + 30.minutes) } },
            makeValid = { update(DoseField.TAKEN_AT) { it.copy(takenAt = at(yesterday, 22, 0)) } },
            invalidMessage = "Tiden kan inte vara senare än nu.",
        ) { state, effects, onSave, onClose ->
            DoseEditScreen(EntryForm(true, state, effects, { if (it == EntryEditEvent.Save) onSave() }), onClose, DoseMode.AS_NEEDED, zone, AsNeededState(alvedon))
        }
    }

    @Test
    fun `en tagen dos uppfyller redigeringskontraktet (MED-15, NFR-10)`() {
        val prn = later.copy(id = "p", createdAt = created)
        val editor = EditorState(prn, doseValidator({ now }))
        editor.load(prn)
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(DoseField.NAME) { it.copy(name = "") } },
            makeValid = { update(DoseField.NAME) { it.copy(name = "Alvedon Forte") } },
            invalidMessage = "Ange ett namn",
        ) { state, effects, onSave, onClose ->
            DoseEditScreen(EntryForm(false, state, effects, { if (it == EntryEditEvent.Save) onSave() }, prn), onClose, DoseMode.EDIT, zone)
        }
    }

    @Test
    fun `en engångsdos visar och ändrar styrkan (REC-14)`() {
        var value by mutableStateOf(oneOff.copy(strength = "3 mg"))
        rule.setContent { DagbokenTheme { DoseEditScreen(form(value) { e -> if (e is EntryEditEvent.Changed) value = e.change(value) }, {}, DoseMode.NEW, zone) } }
        rule.onNodeWithText("3 mg").assertIsDisplayed()
        rule.onNodeWithText("3 mg").performTextReplacement("5 mg")
        assertEquals("5 mg", value.strength)
    }

    @Test
    fun `en engångsdos väljer enhet och tidpunkt (MED-11)`() {
        var value by mutableStateOf(oneOff)
        rule.setContent { DagbokenTheme { DoseEditScreen(form(value) { e -> if (e is EntryEditEvent.Changed) value = e.change(value) }, {}, DoseMode.NEW, zone) } }
        rule.onNodeWithText("Ny dos").assertIsDisplayed()
        rule.onNodeWithText("ml").performScrollTo().performClick()
        rule.onNodeWithText("Kväll").performScrollTo().performClick()
        assertEquals("ml" to Slot.EVENING, value.unit to value.slot)
        rule.onNodeWithText("Tagen").assertDoesNotExist()
    }

    @Test
    fun `en receptdos visar namn, dos och tidpunkt skrivskyddat, och Tagen av är överhoppad (MED-15)`() {
        var value by mutableStateOf(recipe)
        rule.setContent {
            DagbokenTheme { DoseEditScreen(form(value, isNew = false, stored = recipe) { e -> if (e is EntryEditEvent.Changed) value = e.change(value) }, {}, DoseMode.EDIT, zone) }
        }
        rule.onNodeWithText("Levaxin 100 µg").assertIsDisplayed()
        rule.onNodeWithText("Namn, dos och tidpunkt styrs av receptet.").assertIsDisplayed()
        rule.onNodeWithText("Namn").assertDoesNotExist()
        rule.onNodeWithText("Tagen").performScrollTo().performClick()
        assertEquals(DoseStatus.SKIPPED to null, value.status to value.takenAt)
        rule.onNodeWithText("Tagen").assertIsOff()
        rule.onNodeWithText("Tid").assertDoesNotExist()
    }

    @Test
    fun `en dos utan recept har inget Tagen – den raderas i stället (MED-3, MED-15)`() {
        val prn = later.copy(id = "p", createdAt = created)
        rule.setContent { DagbokenTheme { DoseEditScreen(form(prn, isNew = false, stored = prn), {}, DoseMode.EDIT, zone) } }
        rule.onNodeWithText("Redigera dos").assertIsDisplayed()
        rule.onNodeWithText("Namn").assertIsDisplayed()
        rule.onNodeWithText("Tagen").assertDoesNotExist()
    }

    @Test
    fun `Radera på en receptdos säger att den hoppas över, på en vid behov-dos att den raderas (MED-15, HIST-5)`() {
        var shown by mutableStateOf(recipe)
        val events = mutableListOf<EntryEditEvent<Dose>>()
        rule.setContent { DagbokenTheme { DoseEditScreen(form(shown, isNew = false, stored = shown) { events += it }, {}, DoseMode.EDIT, zone) } }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Dosen Levaxin 100 µg, 5 okt kl. 07:12 markeras som överhoppad och försvinner ur Dagbok. Receptet och anteckningen står kvar.").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<EntryEditEvent<Dose>>(EntryEditEvent.Delete), events)

        shown = later.copy(id = "p")
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Dosen Alvedon 500 mg, 5 okt kl. 21:30 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
    }

    @Test
    fun `i efterhand visar medicinen, kylperioden och dagsgränsen vid den valda tiden och frågar För tidigt (FAV-4, FAV-5, MED-16)`() {
        var asNeeded by mutableStateOf(AsNeededState(alvedon, PrnCheck.Cooldown(80.minutes)))
        var confirmed = 0
        rule.setContent { DagbokenTheme { DoseEditScreen(form(later), {}, DoseMode.AS_NEEDED, zone, asNeeded) } }
        rule.onNodeWithText("Logga i efterhand").assertIsDisplayed()
        rule.onNodeWithText("Alvedon 500 mg").assertIsDisplayed()
        rule.onNodeWithText("Minst 4 h mellan · högst 3 per dag").assertIsDisplayed()
        rule.onNodeWithText("För tidigt vid den tiden: 1h 20m kvar av kylperioden för Alvedon.").assertIsDisplayed()
        rule.onNodeWithText("Namn").assertDoesNotExist()

        asNeeded = AsNeededState(alvedon, PrnCheck.DailyLimitReached)
        rule.onNodeWithText("Högst 3 doser per dag – gränsen är nådd för Alvedon.").assertIsDisplayed()

        asNeeded = AsNeededState(alvedon, PrnCheck.Cooldown(80.minutes), CooldownPrompt(alvedon, 80.minutes), onConfirmCooldown = { confirmed++ })
        rule.onNodeWithText("Du bör vänta 1h 20m till för Alvedon. Vill du ta ändå?").assertIsDisplayed()
        rule.onNodeWithText("Ta ändå").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `dosvalet öppnar en vid behov-medicin i efterhand eller en engångsdos (NAV-10, MEDF-6)`() {
        val opened = mutableListOf<LogTarget>()
        rule.setContent { DagbokenTheme { DosePickerSheet(DosePicker(yesterday, listOf(alvedon, ipren)), {}) { opened += it } } }
        rule.onNodeWithText("Logga dos").assertIsDisplayed()
        rule.onNodeWithText("Ipren 400 mg").performClick()
        rule.onNodeWithText("Engångsdos").performClick()
        assertEquals(listOf(LogTarget.AsNeeded("ipren", yesterday), LogTarget.OneOffDose(yesterday)), opened)
    }

    // ── Sjukdom ───────────────────────────────────────────────────────────

    @Test
    fun `sjukdomsvalet checkar in på den pågående eller öppnar en ny episod (SJ-1, SJ-2)`() {
        val opened = mutableListOf<LogTarget>()
        var picker by mutableStateOf(IllnessPicker(null, flu, 4))
        rule.setContent { DagbokenTheme { IllnessPickerSheet(picker, {}) { opened += it } } }
        rule.onNodeWithText("Dag 4").assertIsDisplayed()
        rule.onNodeWithText("Checka in på Förkylning").performClick()
        rule.onNodeWithText("Ny sjukdomsepisod").performClick()
        assertEquals(listOf(LogTarget.Checkin("flu", null), LogTarget.NewEpisode(null)), opened)
        picker = IllnessPicker(null, null, null)
        rule.onNodeWithText("Checka in på Förkylning").assertDoesNotExist()
    }

    @Test
    fun `en ny episod uppfyller redigeringskontraktet – typen krävs (SJ-1, NFR-10)`() {
        val editor = EditorState(start, episodeStartValidator { day })
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(IllnessField.TYPE) { it.copy(episode = it.episode.copy(type = "")) } },
            makeValid = { update(IllnessField.TYPE) { it.copy(episode = it.episode.copy(type = "Förkylning")) } },
            invalidMessage = "Ange vilken sjukdom",
        ) { state, effects, onSave, onClose ->
            EpisodeNewScreen(EntryForm(true, state, effects, { if (it == EntryEditEvent.Save) onSave() }), onClose, symptoms)
        }
    }

    @Test
    fun `en episod uppfyller redigeringskontraktet – typen krävs (SJ-12, NFR-10)`() {
        val editor = EditorState(flu, episodeValidator { day })
        editor.load(flu)
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(IllnessField.TYPE) { it.copy(type = " ") } },
            makeValid = { update(IllnessField.TYPE) { it.copy(type = "Influensa") } },
            invalidMessage = "Ange vilken sjukdom",
        ) { state, effects, onSave, onClose ->
            EpisodeEditScreen(EntryForm(false, state, effects, { if (it == EntryEditEvent.Save) onSave() }, flu), onClose)
        }
    }

    @Test
    fun `episodens formulär har typ, startdatum och anteckning men ingen incheckning (SJ-8, SJ-12)`() {
        rule.setContent { DagbokenTheme { EpisodeEditScreen(form(flu.copy(note = "Halsen"), isNew = false, stored = flu), {}) } }
        rule.onNodeWithText("Redigera sjukdomsepisod").assertIsDisplayed()
        rule.onNodeWithText("Förkylning").assertIsDisplayed()
        rule.onNodeWithContentDescription("Startdatum", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Halsen").assertIsDisplayed()
        rule.onNodeWithContentDescription("Svårighetsgrad").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
    }

    @Test
    fun `en incheckning uppfyller redigeringskontraktet (SJ-2, SJ-11, NFR-10)`() {
        val editor = EditorState(checkin, checkinValidator)
        editor.load(checkin)
        rule.runEditScreenContract(
            editor,
            makeInvalid = null,
            makeValid = { update { it.copy(severity = 3) } },
            invalidMessage = null,
        ) { state, effects, onSave, onClose ->
            CheckinEditScreen(EntryForm(false, state, effects, { if (it == EntryEditEvent.Save) onSave() }, checkin), onClose, flu, symptoms)
        }
    }

    @Test
    fun `incheckningen visar episoden med dag N och raderas efter bekräftelse (SJ-11, HIST-5)`() {
        val events = mutableListOf<EntryEditEvent<Checkin>>()
        rule.setContent { DagbokenTheme { CheckinEditScreen(form(checkin, isNew = false, stored = checkin) { events += it }, {}, flu, symptoms) } }
        rule.onNodeWithText("Redigera incheckning").assertIsDisplayed()
        rule.onNodeWithText("Förkylning").assertIsDisplayed()
        rule.onNodeWithText("Dag 4").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Incheckningen för Förkylning, 6 okt kl. 08:30 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<EntryEditEvent<Checkin>>(EntryEditEvent.Delete), events)
    }

    @Test
    fun `en ny episod har typ, startdatum, svårighetsgrad, symptom och anteckning (SJ-1, SJ-2, SJ-3, SJ-8)`() {
        var value by mutableStateOf(start)
        rule.setContent { DagbokenTheme { EpisodeNewScreen(form(value) { e -> if (e is EntryEditEvent.Changed) value = e.change(value) }, {}, symptoms) } }
        rule.onNodeWithText("Ny sjukdomsepisod").assertIsDisplayed()
        rule.onNodeWithText("T.ex. Förkylning eller Influensa").assertIsDisplayed()
        rule.onNodeWithContentDescription("Startdatum", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Svårighetsgrad").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Symptom").performScrollTo().performClick()
        rule.onNodeWithText("Halsont").performScrollTo().performClick()
        assertEquals(listOf("throat"), value.checkin.symptoms.map { it.optionId })
        rule.onNodeWithText("Lägg till en anteckning").performScrollTo().assertIsDisplayed()
    }

    // ── Skärmdumpar bredvid mockupen ──────────────────────────────────────

    @Test
    fun `skärmdump - ny dos engångs`() = rule.captureLightAndDark("Log_dos_engangs") {
        DoseEditScreen(form(oneOff.copy(note = "Svårt att somna")), {}, DoseMode.NEW, zone)
    }

    @Test
    fun `skärmdump - dosvalet`() = rule.captureScreenLightAndDark("Log_dos_val") {
        DosePickerSheet(DosePicker(null, listOf(alvedon, ipren)), {}) {}
    }

    @Test
    fun `skärmdump - vid behov i efterhand för tidigt`() = rule.captureLightAndDark("Log_dos_efterhand_for_tidigt") {
        DoseEditScreen(form(later), {}, DoseMode.AS_NEEDED, zone, AsNeededState(alvedon, PrnCheck.Cooldown(80.minutes)))
    }

    @Test
    fun `skärmdump - redigera tagen receptdos`() = rule.captureLightAndDark("Log_dos_receptdos") {
        DoseEditScreen(form(recipe, isNew = false, stored = recipe), {}, DoseMode.EDIT, zone)
    }

    @Test
    fun `skärmdump - sjukdom checka in eller ny`() = rule.captureScreenLightAndDark("Log_sjukdom_val") {
        IllnessPickerSheet(IllnessPicker(null, flu, 4), {}) {}
    }

    @Test
    fun `skärmdump - ny sjukdomsepisod`() = rule.captureLightAndDark("Log_sjukdom_ny") {
        EpisodeNewScreen(form(start.copy(checkin = start.checkin.copy(symptoms = listOf(SymptomScore("throat", 6))))), {}, symptoms)
    }

    @Test
    fun `skärmdump - redigera sjukdomsepisod`() = rule.captureLightAndDark("Log_sjukdom_redigera") {
        EpisodeEditScreen(form(flu.copy(note = "Började efter jobbresan."), isNew = false, stored = flu), {})
    }

    @Test
    fun `skärmdump - ny incheckning`() = rule.captureLightAndDark("Log_incheckning_ny") {
        CheckinEditScreen(form(checkin), {}, flu, symptoms)
    }
}
