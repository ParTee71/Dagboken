package se.partee71.dagboken.ui.log

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import org.junit.Rule
import org.junit.Test
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OTHER_ACTIVITY_ID
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.core.schema.TextLimits
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.FixedClock
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.firestore.Paths
import se.partee71.dagboken.data.repository.DefaultActivityRepository
import se.partee71.dagboken.data.repository.DefaultEventRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.testing.MainDispatcherRule
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EntryEditEvent

/**
 * Aktivitets-, händelse- och sjukdomsformulären (AKT-1–AKT-12, HAN-1, SJ-1, SJ-2, SJ-11, NFR-10–12) mot `FakeCollection` och en fast klocka:
 * tisdag 6 oktober 2026 kl. 14:20 i Europe/Stockholm. Förval, validering, sparning av ny och ändrad, och radering.
 */
class LogFormsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val clock = FixedClock(LocalDateTime(today, LocalTime(14, 20)).toInstant(zone))
    private val factory = FakeCollectionFactory(clock = clock)
    private val activities = DefaultActivityRepository(factory, clock)
    private val events = DefaultEventRepository(factory, clock)
    private val options = DefaultOptionsRepository(factory)

    private fun activityForm(id: String? = null, date: LocalDate? = null) = ActivityEditViewModel(activities, options, clock, { zone }, id, date)

    private fun eventForm(id: String? = null, date: LocalDate? = null) = EventEditViewModel(events, options, clock, { zone }, id, date)

    private val ActivityEditViewModel.value: Activity get() = editor.state.value.value
    private val EventEditViewModel.value: Event get() = editor.state.value.value

    private fun ActivityEditViewModel.change(field: String? = null, transform: (Activity) -> Activity) = form.onEvent(EntryEditEvent.Changed(field, transform))

    private fun EventEditViewModel.change(field: String? = null, transform: (Event) -> Event) = form.onEvent(EntryEditEvent.Changed(field, transform))

    // ── Aktivitet ─────────────────────────────────────────────────────────

    @Test
    fun `en ny aktivitet loggas mot den visade dagen kl nu och förifylls med senaste typ och tidsåtgång (AKT-12, HEM-14)`() = runTest(main.dispatcher) {
        factory.activities().upsert(Activity("förra", LocalDate(2026, 10, 4), LocalTime(9, 0), optionId = "walk", minutes = 45, energy = 4)).getOrThrow()
        val vm = activityForm(date = yesterday)
        assertTrue(vm.form.isNew)
        assertEquals(yesterday, vm.value.date)
        assertEquals(LocalTime(14, 20), vm.value.time)
        assertEquals("walk" to 45, vm.value.optionId to vm.value.minutes)
        assertEquals(0, vm.value.energy, "bara typ och tidsåtgång förifylls")
        assertEquals(clock.now(), vm.value.createdAt)
        assertFalse(vm.editor.state.value.canSave, "förvalen är utgångsläget – Spara först när något ändrats (AKT-9)")
        assertEquals(today, activityForm().value.date, "utan dag: idag")
    }

    @Test
    fun `typ krävs och Övrigt kräver en beskrivning (AKT-2, AKT-9)`() = runTest(main.dispatcher) {
        val vm = activityForm()
        vm.change { it.copy(stress = 3) }
        assertFalse(vm.editor.state.value.canSave, "ingen typ")
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            expectNoEvents()
        }
        assertEquals(R.string.entry_type_missing, vm.editor.state.value.errorFor(ActivityField.TYPE), "felet syns efter sparförsöket")

        vm.change(ActivityField.TYPE) { it.copy(optionId = OTHER_ACTIVITY_ID) }
        assertEquals(R.string.activity_describe_missing, vm.editor.state.value.errorFor(ActivityField.DESCRIPTION))
        vm.change(ActivityField.DESCRIPTION) { it.copy(customText = "  ") }
        assertFalse(vm.editor.state.value.canSave, "blanksteg är ingen beskrivning")
        vm.change(ActivityField.DESCRIPTION) { it.copy(customText = "Svamplockning") }
        assertTrue(vm.editor.state.value.canSave)
        vm.change(ActivityField.TYPE) { it.copy(optionId = "walk") }
        assertNull(vm.editor.state.value.errorFor(ActivityField.DESCRIPTION), "bara Övrigt kräver beskrivning")
    }

    @Test
    fun `valideringen speglar rules – en lagrad text över taket och ett värde utanför skalan går inte att spara`() = runTest(main.dispatcher) {
        val tooLong = "x".repeat(TextLimits.LONG + 1)
        assertEquals(R.string.field_not_savable, activityValidator.validate(Activity("a", optionId = "walk", note = tooLong))["note"])
        assertEquals(R.string.field_not_savable, activityValidator.validate(Activity("a", optionId = "walk", energy = 11))["energy"])
        assertEquals(R.string.field_not_savable, activityValidator.validate(Activity("a", optionId = "walk", symptoms = listOf(SymptomScore("s", 12))))["symptoms"])
        assertEquals(emptyMap(), activityValidator.validate(Activity("a", optionId = "walk", energy = -10, stress = 10)))
        assertEquals(R.string.field_not_savable, eventValidator.validate(Event("e", optionId = "migraine", triggers = tooLong))[EventField.TRIGGERS])
    }

    @Test
    fun `en ny aktivitet sparas med sitt id och sin skapandetid, trimmad och med beskrivningen bara vid Övrigt (AKT-1–AKT-11)`() = runTest(main.dispatcher) {
        val vm = activityForm()
        val id = vm.value.id
        vm.change(ActivityField.TYPE) { it.copy(optionId = OTHER_ACTIVITY_ID) }
        vm.change(ActivityField.DESCRIPTION) { it.copy(customText = " Svamplockning ") }
        vm.change { it.copy(recovering = true, drain = true, minutes = 90, energy = -3, stress = 2, symptoms = listOf(SymptomScore("h", 4)), note = " Regn ") }
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val saved = factory.activities().get(id).getOrThrow()
        assertEquals(
            Activity(
                id, today, LocalTime(14, 20), OTHER_ACTIVITY_ID, "Svamplockning", energy = -3, stress = 2, symptoms = listOf(SymptomScore("h", 4)),
                recovering = true, drain = true, minutes = 90, createdAt = clock.now(), note = "Regn",
            ),
            saved,
        )

        val other = activityForm()
        other.change(ActivityField.TYPE) { it.copy(optionId = OTHER_ACTIVITY_ID) }
        other.change(ActivityField.DESCRIPTION) { it.copy(customText = "Bad") }
        other.change(ActivityField.TYPE) { it.copy(optionId = "walk") }
        other.editor.effects.test {
            other.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.activities().get(other.value.id).getOrThrow()?.customText, "beskrivningen följer inte med en annan typ")
    }

    @Test
    fun `en ändrad aktivitet läses, skriver bara ändrade fält och behåller okända fält – och raderas efter bekräftelse (AKT-9, HIST-3)`() = runTest(main.dispatcher) {
        val stored = Activity("a1", yesterday, LocalTime(9, 0), optionId = "walk", energy = 2, note = "Med hunden", createdAt = Instant.fromEpochSeconds(1_790_000_000))
        val path = Paths.activities(factory.scope.uid.value!!)
        factory.store.set(path, "a1", ActivityCodec.encode(stored) + ("framtidaFält" to "kvar"), merge = false)
        val vm = activityForm(id = "a1")
        assertFalse(vm.form.isNew)
        assertEquals(stored, vm.value)
        assertFalse(vm.editor.state.value.canSave)
        vm.change { it.copy(energy = 7) }
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val raw = factory.store.read(path, "a1")!!
        assertEquals(7L, raw["energy"])
        assertEquals("kvar", raw["framtidaFält"])
        assertEquals(stored.copy(energy = 7), factory.activities().get("a1").getOrThrow())

        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.activities().get("a1").getOrThrow())
    }

    @Test
    fun `en aktivitet som inte finns visar läsfel – och sparas aldrig som ny`() = runTest(main.dispatcher) {
        val vm = activityForm(id = "saknas")
        assertEquals(DataError.NotFound, vm.editor.state.value.loadError)
        vm.form.onEvent(EntryEditEvent.Save)
        assertTrue(factory.activities().getAll().getOrThrow().isEmpty())
    }

    @Test
    fun `typerna och symptomen följer Listor medan formuläret är öppet (AKT-1)`() = runTest(main.dispatcher) {
        val vm = activityForm()
        backgroundScope.launch { vm.activityOptions.collect {} }
        backgroundScope.launch { vm.symptomOptions.collect {} }
        options.add(OptionKind.ACTIVITY, "Promenad").getOrThrow()
        options.add(OptionKind.SYMPTOM, "Yrsel").getOrThrow()
        options.add(OptionKind.EVENT, "Migrän").getOrThrow()
        runCurrent()
        assertEquals(listOf("Promenad"), vm.activityOptions.value.map(Option::name))
        assertEquals(listOf("Yrsel"), vm.symptomOptions.value.map(Option::name))
    }

    // ── Händelse ──────────────────────────────────────────────────────────

    @Test
    fun `en ny händelse loggas mot den visade dagen kl nu med svårighetsgrad 5, och typ krävs (HAN-1, HEM-14)`() = runTest(main.dispatcher) {
        val vm = eventForm(date = yesterday)
        assertEquals(yesterday to LocalTime(14, 20), vm.value.date to vm.value.time)
        assertEquals(EventRepository.DEFAULT_SEVERITY, vm.value.severity)
        assertFalse(vm.editor.state.value.canSave)
        vm.change { it.copy(severity = 8) }
        assertFalse(vm.editor.state.value.canSave, "ingen typ")
        vm.change(EventField.TYPE) { it.copy(optionId = "migraine") }
        vm.change(EventField.TRIGGERS) { it.copy(triggers = " Starkt ljus ") }
        vm.change(EventField.ACTIONS) { it.copy(actions = "  ") }
        vm.change { it.copy(durationMinutes = 120, note = "Aura först") }
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(
            Event(vm.value.id, yesterday, LocalTime(14, 20), "migraine", 8, 120, "Starkt ljus", null, clock.now(), "Aura först"),
            factory.events().get(vm.value.id).getOrThrow(),
            "trimmat, och en tom text sparas som ingen",
        )
    }

    @Test
    fun `en ändrad händelse skriver bara ändrade fält, och raderas efter bekräftelse (HAN-1, HIST-3)`() = runTest(main.dispatcher) {
        val stored = Event("e1", yesterday, LocalTime(9, 0), optionId = "migraine", severity = 4, triggers = "Stress", createdAt = Instant.fromEpochSeconds(1_790_000_000))
        val path = Paths.events(factory.scope.uid.value!!)
        factory.store.set(path, "e1", EventCodec.encode(stored) + ("framtidaFält" to "kvar"), merge = false)
        val vm = eventForm(id = "e1")
        assertEquals(stored, vm.value)
        vm.change(EventField.TYPE) { it.copy(optionId = "") }
        assertEquals(R.string.entry_type_missing, vm.editor.state.value.errorFor(EventField.TYPE))
        vm.change(EventField.TYPE) { it.copy(optionId = "dizzy") }
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val raw = factory.store.read(path, "e1")!!
        assertEquals("dizzy", raw["optionId"])
        assertEquals("kvar", raw["framtidaFält"])
        assertEquals(stored.copy(optionId = "dizzy"), factory.events().get("e1").getOrThrow())

        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.events().get("e1").getOrThrow())
    }

    @Test
    fun `samma symptom igen gör inte formuläret ändrat och behåller 3x-somatiska (DAT-6)`() = runTest(main.dispatcher) {
        val stored = Activity("a1", today, LocalTime(9, 0), optionId = "walk", symptoms = listOf(SymptomScore("h", null)), legacySomatic = 3)
        factory.activities().upsert(stored).getOrThrow()
        val vm = activityForm(id = "a1")
        vm.change { it.withSymptoms(it.symptoms.toList()) }
        assertFalse(vm.editor.state.value.isDirty)
        assertEquals(3, vm.value.somatic)
        vm.change { it.withSymptoms(listOf(SymptomScore("h", 2))) }
        assertTrue(vm.editor.state.value.isDirty)
        assertEquals(null to 2, vm.value.legacySomatic to vm.value.somatic)
    }

    @Test
    fun `ett sparfel visas och formuläret står kvar (NFR-12)`() = runTest(main.dispatcher) {
        val failing = object : EventRepository by events {
            override suspend fun save(loaded: Event?, edited: Event): Result<Unit> = Result.failure(DataError.Offline)
        }
        val vm = EventEditViewModel(failing, options, clock, { zone }, null, null)
        vm.change(EventField.TYPE) { it.copy(optionId = "migraine") }
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(DataError.Offline, (awaitItem() as EditorEffect.Failed).failure.error)
        }
        assertTrue(vm.editor.state.value.isDirty)
    }

    @Test
    fun `valideringen gäller det som sparas – text som ryms trimmad går att spara`() = runTest(main.dispatcher) {
        val vm = activityForm()
        vm.change(ActivityField.TYPE) { it.copy(optionId = "walk") }
        vm.change(ActivityField.NOTE) { it.copy(note = " ".repeat(10) + "x".repeat(TextLimits.LONG)) }
        assertNull(vm.editor.state.value.errorFor(ActivityField.NOTE), "trimmad ryms den")
        assertTrue(vm.editor.state.value.canSave)
        vm.change(ActivityField.NOTE) { it.copy(note = "x".repeat(TextLimits.LONG + 1)) }
        assertEquals(R.string.field_not_savable, vm.editor.state.value.errorFor(ActivityField.NOTE))
        assertFalse(vm.editor.state.value.canSave)
    }

    @Test
    fun `en äldre beskrivning på en annan typ står kvar vid en ändring som inte rör typen – byts typen bort från Övrigt töms den`() = runTest(main.dispatcher) {
        val path = Paths.activities(factory.scope.uid.value!!)
        val legacy = Activity("a1", yesterday, LocalTime(9, 0), optionId = "walk", customText = "Med hunden", createdAt = Instant.fromEpochSeconds(1_790_000_000))
        val other = Activity("a2", yesterday, LocalTime(10, 0), optionId = OTHER_ACTIVITY_ID, customText = "Svamplockning", createdAt = Instant.fromEpochSeconds(1_790_000_000))
        factory.store.set(path, "a1", ActivityCodec.encode(legacy), merge = false)
        factory.store.set(path, "a2", ActivityCodec.encode(other), merge = false)

        val first = activityForm(id = "a1")
        first.change { it.copy(energy = 3) }
        first.editor.effects.test {
            first.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals("Med hunden", factory.activities().get("a1").getOrThrow()?.customText)

        val second = activityForm(id = "a2")
        second.change(ActivityField.TYPE) { it.copy(optionId = "walk") }
        second.editor.effects.test {
            second.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.activities().get("a2").getOrThrow()?.customText)
    }

    @Test
    fun `en lagrad text över taket visar sitt fel direkt när formuläret öppnas`() = runTest(main.dispatcher) {
        val stored = Activity("a1", yesterday, LocalTime(9, 0), optionId = "walk", note = "x".repeat(TextLimits.LONG + 1), createdAt = Instant.fromEpochSeconds(1_790_000_000))
        factory.store.set(Paths.activities(factory.scope.uid.value!!), "a1", ActivityCodec.encode(stored), merge = false)
        val vm = activityForm(id = "a1")
        assertEquals(R.string.field_not_savable, vm.editor.state.value.errorFor(ActivityField.NOTE))
    }

    // ── Sjukdom (SJ-1, SJ-2, SJ-3, SJ-8, SJ-11) ──────────────────────────

    private val illnesses = DefaultIllnessRepository(factory, clock)

    private fun episodeForm(date: LocalDate? = null) = EpisodeNewViewModel(illnesses, options, clock, { zone }, date)

    private fun checkinForm(episodeId: String, id: String? = null, date: LocalDate? = null) = CheckinEditViewModel(illnesses, options, clock, { zone }, episodeId, id, date)

    private val flu = IllnessEpisode("flu", "Förkylning", LocalDate(2026, 10, 3), createdAt = Instant.fromEpochSeconds(1_790_000_000))

    @Test
    fun `en ny episod börjar den visade dagen med svårighetsgrad 5, och typen krävs (SJ-1, HEM-14)`() = runTest(main.dispatcher) {
        val vm = episodeForm(date = yesterday)
        val start = vm.editor.state.value.value
        assertEquals(yesterday, start.episode.start)
        assertNull(start.episode.end, "pågående")
        assertEquals(yesterday to LocalTime(14, 20), start.checkin.date to start.checkin.time)
        assertEquals(IllnessRepository.DEFAULT_SEVERITY, start.checkin.severity)
        vm.form.onEvent(EntryEditEvent.Changed(null) { it.copy(checkin = it.checkin.copy(severity = 7)) })
        assertFalse(vm.editor.state.value.canSave, "ingen typ")
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            expectNoEvents()
        }
        assertEquals(R.string.illness_type_missing, vm.editor.state.value.errorFor(IllnessField.TYPE))
        assertTrue(factory.illnessEpisodes().getAll().getOrThrow().isEmpty())
    }

    @Test
    fun `en ny episod sparas med sin första incheckning på startdagen – trimmad och med anteckningen på episoden (SJ-1, SJ-2, SJ-3, SJ-8)`() = runTest(main.dispatcher) {
        val vm = episodeForm()
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.TYPE) { it.copy(episode = it.episode.copy(type = " Influensa ")) })
        vm.form.onEvent(EntryEditEvent.Changed(null) { it.copy(episode = it.episode.copy(start = yesterday), checkin = it.checkin.copy(date = yesterday)) })
        vm.form.onEvent(EntryEditEvent.Changed(null) { it.copy(checkin = it.checkin.copy(severity = 8, symptoms = listOf(SymptomScore("h", 6)))) })
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.NOTE) { it.copy(episode = it.episode.copy(note = " Feber ")) })
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        val value = vm.editor.state.value.value
        assertEquals(IllnessEpisode(value.episode.id, "Influensa", yesterday, createdAt = clock.now(), note = "Feber"), illnesses.getEpisode(value.episode.id).getOrThrow())
        assertEquals(
            listOf(Checkin(value.checkin.id, yesterday, LocalTime(14, 20), 8, listOf(SymptomScore("h", 6)), clock.now())),
            illnesses.observeCheckins(value.episode.id).first(),
        )
    }

    @Test
    fun `en ny incheckning skrivs bara under en episod som finns (SJ-2)`() = runTest(main.dispatcher) {
        val missing = checkinForm("saknas")
        assertEquals(DataError.NotFound, missing.editor.state.value.loadError)
        missing.form.onEvent(EntryEditEvent.Save)
        assertTrue(factory.checkins("saknas").getAll().getOrThrow().isEmpty())

        factory.illnessEpisodes().upsert(flu).getOrThrow()
        val vm = checkinForm("flu", date = yesterday)
        backgroundScope.launch { vm.episode.collect {} }
        runCurrent()
        assertEquals(flu, vm.episode.value)
        assertEquals(yesterday to LocalTime(14, 20), vm.editor.state.value.value.let { it.date to it.time })
        vm.form.onEvent(EntryEditEvent.Changed(null) { it.copy(severity = 2) })
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(2, illnesses.observeCheckins("flu").first().single().severity)
    }

    @Test
    fun `en incheckning ändras och raderas under sin episod (SJ-11, HIST-5)`() = runTest(main.dispatcher) {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        val stored = Checkin("c", yesterday, LocalTime(9, 0), severity = 4, createdAt = Instant.fromEpochSeconds(1_790_000_000))
        factory.checkins("flu").upsert(stored).getOrThrow()
        val vm = checkinForm("flu", id = "c")
        assertFalse(vm.form.isNew)
        assertEquals(stored, vm.editor.state.value.value)
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.NOTE) { it.copy(note = " Bättre ") })
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(stored.copy(note = "Bättre"), factory.checkins("flu").get("c").getOrThrow())
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Delete)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertNull(factory.checkins("flu").get("c").getOrThrow())
        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow(), "episoden står kvar")
    }

    @Test
    fun `en episod redigeras utan att någon incheckning skapas – slutet och skapandetiden står kvar (SJ-12)`() = runTest(main.dispatcher) {
        val ended = flu.copy(end = today, note = "Halsen")
        factory.illnessEpisodes().upsert(ended).getOrThrow()
        val vm = EpisodeEditViewModel(illnesses, clock, { zone }, "flu")
        assertEquals(ended, vm.editor.state.value.value)
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.START) { it.copy(start = LocalDate(2026, 10, 7)) })
        assertEquals(R.string.prescription_error_end_before_start, vm.editor.state.value.errorFor(IllnessField.START))
        assertFalse(vm.editor.state.value.canSave, "starten efter slutet")
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.START) { it.copy(start = yesterday) })
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.TYPE) { it.copy(type = " Influensa ") })
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.NOTE) { it.copy(note = " ") })
        vm.editor.effects.test {
            vm.form.onEvent(EntryEditEvent.Save)
            assertEquals(EditorEffect.Done, awaitItem())
        }
        assertEquals(ended.copy(type = "Influensa", start = yesterday, note = null), illnesses.getEpisode("flu").getOrThrow())
        assertTrue(illnesses.observeCheckins("flu").first().isEmpty(), "ingen incheckning")
    }

    @Test
    fun `en episod som inte finns kan inte redigeras (SJ-12)`() = runTest(main.dispatcher) {
        val vm = EpisodeEditViewModel(illnesses, clock, { zone }, "saknas")
        assertEquals(DataError.NotFound, vm.editor.state.value.loadError)
    }

    @Test
    fun `ett lagrat slutdatum efter idag går inte att spara – samma regel som Avsluta (SJ-4, SJ-12)`() = runTest(main.dispatcher) {
        factory.illnessEpisodes().upsert(flu.copy(end = LocalDate(2026, 10, 9))).getOrThrow()
        val vm = EpisodeEditViewModel(illnesses, clock, { zone }, "flu")
        assertEquals(R.string.illness_end_after_today, vm.editor.state.value.errorFor(IllnessField.START))
        vm.form.onEvent(EntryEditEvent.Changed(IllnessField.TYPE) { it.copy(type = "Influensa") })
        assertFalse(vm.editor.state.value.canSave)
    }

    @Test
    fun `episodens formulär raderar aldrig – kaskaden finns bara i sjukdomsdetaljen (SJ-9)`() = runTest(main.dispatcher) {
        factory.illnessEpisodes().upsert(flu).getOrThrow()
        factory.checkins("flu").upsert(Checkin("c", yesterday, LocalTime(9, 0), severity = 4, createdAt = flu.createdAt)).getOrThrow()
        val vm = EpisodeEditViewModel(illnesses, clock, { zone }, "flu")
        vm.form.onEvent(EntryEditEvent.Delete)
        runCurrent()
        assertEquals(flu, illnesses.getEpisode("flu").getOrThrow())
        assertEquals(1, illnesses.observeCheckins("flu").first().size)
    }
}
