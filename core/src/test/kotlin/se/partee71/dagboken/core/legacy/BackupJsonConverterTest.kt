package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.OptionCodec
import se.partee71.dagboken.core.schema.Samples
import se.partee71.dagboken.core.schema.fieldPaths

/**
 * Konverteraren mot fixturerna (OMB-3): konvertera → exportformat → fältvis jämförelse mot den
 * förväntade exporten, för v2 (varje fält satt) och v1. Plus rapporten, determinismen och att varje
 * 4.0-fält faktiskt får ett värde ur 3.x-datan.
 */
class BackupJsonConverterTest {

    private fun convert(name: String, uid: String = LegacyFixtures.UID) = BackupJsonConverter.convert(LegacyFixtures.backup(name), uid)

    private fun converted(name: String) = assertIs<ConversionResult.Converted>(convert(name), "konverteringen ska lyckas")

    @Test
    fun `v2 - varje dokument och fält blir exakt som i den förväntade exporten`() = assertMatchesExpected("backup-v2")

    @Test
    fun `v1 - varje dokument och fält blir exakt som i den förväntade exporten`() = assertMatchesExpected("backup-v1")

    /** Fält för fält per dokument och sedan hela filen (`LegacyFixtures.assertMatchesExpected`, delad med legacy-läsarens test). */
    private fun assertMatchesExpected(name: String) =
        LegacyFixtures.assertMatchesExpected(name, converted(name), BackupJsonConverter.exportedAt(LegacyFixtures.backup(name)))

    @Test
    fun `v2 - rapporten räknar dokument per samling och listar varningarna utan innehåll`() {
        val report = converted("backup-v2").report
        assertEquals(2, report.formatVersion)
        assertEquals("2026-01-15T21:00:00", report.createdAt)
        assertEquals(
            mapOf(
                "users" to 1, "settings" to 1, "options" to 13, "prescriptions" to 3, "prnMedicines" to 2, "doses" to 4,
                "screenings" to 3, "activities" to 2, "events" to 2, "illnessEpisodes" to 2, "checkins" to 2,
            ),
            report.counts,
        )
        assertEquals(emptyList(), report.problems)
        assertEquals(
            listOf(
                "prescriptions/1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9" to "skapad är ett ögonblick, inte ett datum",
                "prescriptions/2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e60" to "okänd upprepning",
                "prescriptions/2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e60" to "skapad är inget datum",
                "doses/recept_6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a_2026-01-15_Morgon" to "notes-posten går före arvsfältet anteckning",
                "screenings/0d4f6a2c-7b1e-4c3a-9f5d-2e8b1a6c3d70" to "ett symptomnamn med kommatecken fogades ihop",
                "screenings/9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b" to "symptom utan poäng",
                "screenings/9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b" to "symptom utan poäng",
                "screenings/9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b" to "timestamp är inte ett giltigt ögonblick",
                "activities/5a6b7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d" to "ett symptomnamn med kommatecken fogades ihop",
                "activities/6b7c8d9e-0f1a-4b2c-9d3e-4f5a6b7c8d9e" to "somatiska 5 skiljer sig från summan av symptompoängen 3",
                "illnessEpisodes/8d9e0f1a-2b3c-4d4e-9f5a-6b7c8d9e0f1a" to "notes-posten går före arvsfältet anteckning",
                "illnessEpisodes/8d9e0f1a-2b3c-4d4e-9f5a-6b7c8d9e0f1a/checkins/a0b1c2d3-e4f5-4a6b-8c7d-9e0f1a2b3c4d" to "ett symptomnamn med kommatecken fogades ihop",
                "notes" to "1 anteckning(ar) med target ACTIVITY utan sin post",
            ),
            report.warnings.map { it.path to it.message.substringBefore(" (").substringBefore(" –") },
        )
        val rendered = report.render()
        for (secret in listOf("Regn", "Med frukost", "Med mat", "Hela familjen", "Feber", "veckovis", "Anteckning utan post", "Svamplockning", "Nackspärr", "nacksparr", "Hosta")) {
            assertTrue(secret !in rendered, "rapporten får inte innehålla texten '$secret'")
        }
        assertTrue("Inga fel" in rendered)
    }

    @Test
    fun `v1 - inga inställningar, 3x standardlistan för händelsetyper, och varning för aktiviteten med ett tillfälles namn`() {
        val result = converted("backup-v1")
        assertNull(result.data.settings)
        assertTrue(result.documents.none { CollectionNames.collectionOf(it.path) == CollectionNames.SETTINGS })
        assertEquals(LegacyDefaults.EVENT_OPTIONS, result.data.options.filter { it.kind == OptionKind.EVENT }.sortedBy { it.sortOrder }.map { it.name })
        assertEquals(1, result.report.warnings.size)
        assertEquals("activities/e3f4a5b6-c7d8-4e9f-8a1b-2c3d4e5f6a7b", result.report.warnings.single().path)
        assertEquals(1, result.report.formatVersion)
    }

    @Test
    fun `deterministisk - två körningar ger identisk utdata, och uid påverkar bara sökvägen`() {
        val first = converted("backup-v2")
        val second = converted("backup-v2")
        assertEquals(first.exportJson(BackupJsonConverter.exportedAt(LegacyFixtures.backup("backup-v2"))), second.exportJson(BackupJsonConverter.exportedAt(LegacyFixtures.backup("backup-v2"))))
        val other = assertIs<ConversionResult.Converted>(convert("backup-v2", uid = "annan"))
        assertEquals(
            first.documents.map { it.path.removePrefix("users/${LegacyFixtures.UID}") to ExportFormat.toJson(it.data) },
            other.documents.map { it.path.removePrefix("users/annan") to ExportFormat.toJson(it.data) },
        )
        assertEquals(first.documents.map { it.path }, first.documents.map { it.path }.sorted(), "sorterade på sökväg")
    }

    @Test
    fun `varje fält i varje 4_0-codec får ett icke-default-värde ur v2-fixturen - inget 3x-fält stannar på vägen`() {
        val byCollection = converted("backup-v2").documents.filter { it.path.count { c -> c == '/' } > 1 }.groupBy { CollectionNames.collectionOf(it.path) }
        assertEquals(Samples.all.map { it.collection }.toSet(), byCollection.keys)
        for ((collection, docs) in byCollection) {
            val entry = Samples.entry(collection)
            val differing = docs.flatMap { entry.fieldsDifferingFromDefault(it.path.substringAfterLast('/'), it.data) }.toSet()
            assertEquals(entry.fieldNames() - NEW_IN_4_0, differing, "$collection: fält som ingen 3.x-post fyller")
        }
        // Inställningsdokumentet har varje fält som codecen skriver, eftersom v2-fixturen har varje 3.x-inställning.
        val settings = byCollection.getValue(CollectionNames.SETTINGS).single().data
        assertEquals(fieldPaths(Samples.entry(CollectionNames.SETTINGS).encoded()), fieldPaths(settings))
    }

    @Test
    fun `alternativen får sina id ur OptionIds, V2-listorna går före v1 och Övrigt skapas när listan saknar det`() {
        val options = converted("backup-v2").data.options
        for (option in options) assertEquals(OptionIds.of(option.kind, option.name), option.id)
        assertTrue(options.none { it.name == "Gammal v1-lista" })
        val activities = options.filter { it.kind == OptionKind.ACTIVITY }.sortedBy { it.sortOrder }
        assertEquals(listOf("Promenad" to true, "Jobb" to false, LegacyDefaults.OTHER to false), activities.map { it.name to it.favorite })
        assertEquals(listOf(false, false, false), activities.map { it.archived })
        val symptoms = options.filter { it.kind == OptionKind.SYMPTOM }.sortedBy { it.sortOrder }
        assertEquals(listOf("Huvudvärk", "Trötthet", "Övrigt", "Ont i nacke, axlar", "Yrsel", "Nackspärr", "Hosta"), symptoms.map { it.name })
        assertEquals(listOf(false, false, false, false, true, true, true), symptoms.map { it.archived }, "okända namn blir arkiverade")
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), symptoms.map { it.sortOrder })
    }

    @Test
    fun `saknade alternativlistor ger 3x standardlistor, tomma listor förblir tomma`() {
        val defaults = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(BackupJson(), "u")).data.options
        assertEquals(LegacyDefaults.ACTIVITY_OPTIONS, defaults.filter { it.kind == OptionKind.ACTIVITY }.map { it.name })
        assertEquals(LegacyDefaults.SYMPTOM_OPTIONS, defaults.filter { it.kind == OptionKind.SYMPTOM }.map { it.name })
        assertEquals(LegacyDefaults.EVENT_OPTIONS, defaults.filter { it.kind == OptionKind.EVENT }.map { it.name })
        val empty = assertIs<ConversionResult.Converted>(
            BackupJsonConverter.convert(BackupJson(aktiviteterOptions = emptyList(), symptomOptionsV2 = emptyList(), handelseTypOptions = emptyList()), "u"),
        )
        assertEquals(emptyList(), empty.data.options)
        assertEquals(1, empty.documents.size, "bara användardokumentet")
    }

    @Test
    fun `inställningar - null i ett 3x-fält skriver inte fältet, och utan inställningar skrivs inget dokument`() {
        val partial = assertIs<ConversionResult.Converted>(
            BackupJsonConverter.convert(BackupJson(settings = SettingsBackup(themeMode = "light", birthYear = 1980), periodReminderTime = "07:30"), "u"),
        )
        val doc = partial.documents.single { CollectionNames.collectionOf(it.path) == CollectionNames.SETTINGS }.data
        assertEquals(
            mapOf("theme" to mapOf("mode" to "light"), "reminders" to mapOf("periodReminderTime" to "07:30"), "profile" to mapOf("birthYear" to 1980)),
            doc,
        )
        assertNull(assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(BackupJson(settings = SettingsBackup()), "u")).data.settings)
    }

    @Test
    fun `screening - tillfället ur namnet, annars ur klockslaget mot backupens påminnelsetider, och namnet bevaras`() {
        val configs = listOf(ScreeningEventConfigJson(true, "06:00"), ScreeningEventConfigJson(true, "11:00"), ScreeningEventConfigJson(false, ""), ScreeningEventConfigJson(true, "23:00"))
        val backup = BackupJson(
            aktiviteter = listOf(
                AktivitetJson(id = "s1", datum = "2026-01-15", tid = "09:00", aktivitet = "Screening", type = "screening"),
                AktivitetJson(id = "s2", datum = "2026-01-15", tid = "23:30", aktivitet = "Kvällsmat", type = "screening"),
            ),
            screeningEventConfigs = configs,
        )
        val screenings = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u")).data.screenings.associateBy { it.id }
        assertEquals(Occasion.LUNCH, screenings.getValue("s1").occasion, "11:00 är närmare 09:00 än 06:00")
        assertEquals("Screening", screenings.getValue("s1").customText)
        assertEquals(Occasion.DINNER, screenings.getValue("s2").occasion, "namnet avgör")
        assertNull(screenings.getValue("s2").customText)
        assertEquals(
            Occasion.derive("x", LocalTime(9, 0), mapOf(Occasion.BREAKFAST to LocalTime(6, 0), Occasion.LUNCH to LocalTime(11, 0), Occasion.BEDTIME to LocalTime(23, 0))),
            screenings.getValue("s1").occasion,
        )
    }

    @Test
    fun `dos - status ur tagen och skipped, takenAt på dosens dag, recept-id oförändrat`() {
        val backup = BackupJson(
            mediciner = listOf(
                MedicinJson(id = "recept_r1_2026-01-15_Morgon", datum = "2026-01-15", tid = "07:00", tidpunkt = "Morgon", tagen = true, skipped = true, tagenTid = "07:30", receptId = "r1"),
                MedicinJson(id = "m2", datum = "2026-01-15", tid = "19:00", tidpunkt = "Kväll", skipped = true),
                MedicinJson(id = "m3", datum = "2026-01-15", tid = "12:00", tidpunkt = "Vid behov", tagenTid = ""),
            ),
        )
        val doses = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u")).data.doses.associateBy { it.id }
        assertEquals(DoseStatus.TAKEN, doses.getValue("recept_r1_2026-01-15_Morgon").status)
        assertEquals(kotlin.time.Instant.parse("2026-01-15T06:30:00Z"), doses.getValue("recept_r1_2026-01-15_Morgon").takenAt)
        assertEquals(DoseStatus.SKIPPED, doses.getValue("m2").status)
        assertEquals(DoseStatus.PLANNED, doses.getValue("m3").status)
        assertNull(doses.getValue("m3").takenAt)
        assertEquals(Slot.AS_NEEDED, doses.getValue("m3").slot)
    }

    @Test
    fun `dos - en tagningstid mer än 12 timmar före det schemalagda klockslaget bevaras men syns i rapporten`() {
        val backup = BackupJson(
            mediciner = listOf(
                MedicinJson(id = "m1", datum = "2026-01-15", tid = "22:00", tidpunkt = "Natt", tagen = true, tagenTid = "01:30"),
                MedicinJson(id = "m2", datum = "2026-01-15", tid = "19:00", tidpunkt = "Kväll", tagen = true, tagenTid = "07:30"),
            ),
        )
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"))
        assertEquals(kotlin.time.Instant.parse("2026-01-15T00:30:00Z"), result.data.doses.first { it.id == "m1" }.takenAt, "värdet ändras inte")
        assertEquals(listOf("doses/m1"), result.report.warnings.filter { "12 timmar före" in it.message }.map { it.path }, "11,5 timmar före är inom gränsen")
    }

    @Test
    fun `recept - synonymerna för upprepning, veckodagar till ISO och v1-tidpunkten`() {
        val backup = BackupJson(
            medicinRecipes = listOf(
                ReceptJson(id = "r1", upprepning = "Specifika dagar", dagar = listOf(0, 6)),
                ReceptJson(id = "r2", upprepning = "var x:e dag", intervalDagar = 5, tidpunkt = "Natt"),
                ReceptJson(id = "r3", upprepning = "Vardagar"),
                ReceptJson(id = "r4", upprepning = "helger", tidpunkt = ""),
            ),
        )
        val prescriptions = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u")).data.prescriptions.associateBy { it.id }
        val r1 = assertIs<Schedule.Repeating>(prescriptions.getValue("r1").schedule)
        assertEquals(Repeat.CUSTOM, r1.repeat)
        assertEquals(setOf(kotlinx.datetime.DayOfWeek.MONDAY, kotlinx.datetime.DayOfWeek.SUNDAY), r1.days)
        assertEquals(Repeat.INTERVAL, assertIs<Schedule.Repeating>(prescriptions.getValue("r2").schedule).repeat)
        assertEquals(listOf(Slot.NIGHT), prescriptions.getValue("r2").slots)
        assertEquals(Repeat.WEEKDAYS, assertIs<Schedule.Repeating>(prescriptions.getValue("r3").schedule).repeat)
        assertEquals(Repeat.WEEKENDS, assertIs<Schedule.Repeating>(prescriptions.getValue("r4").schedule).repeat)
        assertEquals(listOf(Slot.MORNING), prescriptions.getValue("r4").slots, "tom tidpunkt räknas som saknad → Morgon, som 3.x")
    }

    @Test
    fun `symptom - 3x wire-formatet med dubbletter som i 3x och Övrigt utan parenteser`() {
        val backup = BackupJson(
            aktiviteter = listOf(AktivitetJson(id = "a1", aktivitet = "Promenad", symptom = "Huvudvärk:1,Huvudvärk:4,Övrigt stel:2,Övrigt:1", somatiska = 7)),
            symptomOptionsV2 = listOf(SymptomOptionBackup("Huvudvärk")),
        )
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"))
        val symptoms = result.data.activities.single().symptoms
        assertEquals(listOf(4, 2, 1), symptoms.map { it.score }, "dubbletten samlas med den sista poängen")
        assertEquals(listOf(null, "stel", null), symptoms.map { it.customText })
        assertEquals(OptionIds.of(OptionKind.SYMPTOM, "Övrigt"), symptoms[1].optionId)
        assertEquals(symptoms[1].optionId, symptoms[2].optionId)
        assertTrue(result.report.warnings.any { "dubblett" in it.message })
        assertTrue(result.report.warnings.none { "somatiska" in it.message }, "7 = 4 + 2 + 1 som 3.x räknade")
        assertNotEquals(emptyList(), result.documents.filter { CollectionNames.collectionOf(it.path) == CollectionNames.OPTIONS }.map { OptionCodec.decode("x", it.data).name }.filter { it == "Övrigt" })
    }

    private fun symptomsOf(raw: String, somatiska: Int, vararg options: String): Pair<List<se.partee71.dagboken.core.model.SymptomScore>, List<String>> {
        val backup = BackupJson(
            aktiviteter = listOf(AktivitetJson(id = "a1", aktivitet = "Promenad", symptom = raw, somatiska = somatiska)),
            symptomOptionsV2 = options.map(::SymptomOptionBackup),
        )
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"), raw)
        return result.data.activities.single().symptoms to result.report.warnings.map { it.message }
    }

    @Test
    fun `symptom - ett namn med kommatecken fogas ihop som 3x skrev det, Övrigt-fritext och eget namn`() {
        // 3.x encode: "Övrigt (yrsel, kl 14:00):2" – decode delade på kommat och visade bara "kl 14:00)".
        val (other, warnings) = symptomsOf("Huvudvärk:4,Övrigt (yrsel, kl 14:00):2", 6, "Huvudvärk", "Övrigt")
        assertEquals(listOf(4, 2), other.map { it.score })
        assertEquals(listOf(null, "yrsel, kl 14:00"), other.map { it.customText })
        assertEquals(OptionIds.of(OptionKind.SYMPTOM, "Övrigt"), other[1].optionId)
        assertTrue(warnings.any { "kommatecken" in it })
        assertTrue(warnings.none { "somatiska" in it }, "6 = 4 + 2 som 3.x räknade")
        // Tre delar och ett eget alternativ med komma i namnet.
        val (own, _) = symptomsOf("Ont i nacke, axlar, rygg:3,Övrigt (a, b):1", 4, "Ont i nacke, axlar, rygg", "Övrigt")
        assertEquals(listOf(OptionIds.of(OptionKind.SYMPTOM, "Ont i nacke, axlar, rygg") to 3, OptionIds.of(OptionKind.SYMPTOM, "Övrigt") to 1), own.map { it.optionId to it.score })
        assertEquals("a, b", own[1].customText)
    }

    @Test
    fun `symptom - delar utan poäng behåller namnet med poäng null, inget stopp`() {
        val (list, warnings) = symptomsOf("Huvudvärk,Yrsel", 0, "Huvudvärk")
        assertEquals(listOf(OptionIds.of(OptionKind.SYMPTOM, "Huvudvärk") to null, OptionIds.of(OptionKind.SYMPTOM, "Yrsel") to null), list.map { it.optionId to it.score })
        assertTrue(warnings.any { "utan poäng" in it })
        assertTrue(warnings.none { "somatiska" in it }, "summan av poäng null är 0")
        val (word, _) = symptomsOf("Huvudvärk:tre", 0)
        assertEquals(listOf(OptionIds.of(OptionKind.SYMPTOM, "Huvudvärk:tre") to null), word.map { it.optionId to it.score })
        val (trailingEmpty, emptyWarnings) = symptomsOf("Huvudvärk:2,", 2, "Huvudvärk")
        assertEquals(listOf(2), trailingEmpty.map { it.score })
        assertTrue(emptyWarnings.any { "tom symptomdel" in it })
    }

    private fun scoresOf(raw: String, somatiska: Int, vararg options: String): List<Pair<String, Int?>> {
        val names = (options.toList() + listOf("Huvudvärk", "Yrsel", "A", "B", "Övrigt")).distinct()
        return symptomsOf(raw, somatiska, *names.toTypedArray()).first.map { symptom ->
            val option = names.first { OptionIds.of(OptionKind.SYMPTOM, it) == symptom.optionId }
            (symptom.customText?.let { "$option ($it)" } ?: option) to symptom.score
        }
    }

    @Test
    fun `symptom - en dubblett utan poäng skriver aldrig över en poäng`() {
        assertEquals(listOf("Huvudvärk" to 3), scoresOf("Huvudvärk:3,Huvudvärk", 3))
        val (symptoms, _) = symptomsOf("A:3,A:x", 3, "A")
        assertEquals(3, symptoms.single { it.optionId == OptionIds.of(OptionKind.SYMPTOM, "A") }.score, "A:x skriver inte över A:3")
        assertEquals(null, symptoms.single { it.optionId == OptionIds.of(OptionKind.SYMPTOM, "A:x") }.score, "A:x bevaras som eget namn utan poäng")
    }

    @Test
    fun `symptom - tomma delar hoppas över innan något fogas ihop, inget namn får inledande kommatecken`() {
        assertEquals(listOf("A" to 3, "B" to 2), scoresOf("A:3,,B:2", 5))
        assertEquals(listOf("B" to 2), scoresOf(",B:2", 2))
        assertEquals(listOf("Övrigt (x)" to 2), scoresOf(",Övrigt (x):2", 2))
    }

    @Test
    fun `symptom - delar fogas bara ihop till ett känt alternativ eller Övrigt-formen, annars egna symptom utan poäng`() {
        assertEquals(listOf("Yrsel" to null, "Huvudvärk" to 3), scoresOf("Yrsel,Huvudvärk:3", 3))
        assertEquals(listOf("Yrsel" to null, "Övrigt (a, b)" to 2), scoresOf("Yrsel,Övrigt (a, b):2", 2))
        assertEquals(listOf("Ont i nacke, axlar" to 1), scoresOf("Ont i nacke, axlar:1", 1, "Ont i nacke, axlar"))
    }

    @Test
    fun `skapad - datum blir midnatt, ett ögonblick bevaras med varning, annat blir saknat med varning och form`() {
        fun recept(skapad: String, upprepning: String = "dagligen", startDatum: String = "") =
            assertIs<ConversionResult.Converted>(
                BackupJsonConverter.convert(BackupJson(medicinRecipes = listOf(ReceptJson(id = "r1", skapad = skapad, upprepning = upprepning, startDatum = startDatum))), "u"),
                skapad,
            ).let { it.data.prescriptions.single().createdAt to it.report.warnings.map { w -> w.message } }
        assertEquals(kotlin.time.Instant.parse("2025-11-19T23:00:00Z") to emptyList(), recept("2025-11-20"))
        val (instant, instantWarnings) = recept("2025-11-20T08:15:00.000Z", upprepning = "intervall")
        assertEquals(kotlin.time.Instant.parse("2025-11-20T08:15:00Z"), instant)
        assertTrue(instantWarnings.any { "ögonblick" in it && "9999-99-99a99:99:99.999a" in it })
        assertTrue(instantWarnings.any { "intervallet" in it })
        assertTrue(recept("2025-11-20T08:15:00", upprepning = "intervall", startDatum = "2025-12-01").second.none { "intervallet" in it }, "med startdatum gäller det")
        val (missing, missingWarnings) = recept("20/11 2025")
        assertNull(missing)
        assertTrue(missingWarnings.single().let { "99/99 9999" in it && "20/11" !in it }, "formen, aldrig värdet")
    }

    private companion object {
        /** Nya fält i 4.0 utan 3.x-motsvarighet (ARKITEKTUR.md → Fältparitet). */
        val NEW_IN_4_0 = setOf("prnId")
    }

    @Test
    fun `kön - alla tre 3x-värden får sitt 4-punkt-0-namn i både modell och dokument`() {
        for ((legacy, expected) in listOf("man" to Sex.MALE, "kvinna" to Sex.FEMALE, "ej_angivet" to Sex.UNSPECIFIED)) {
            val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(BackupJson(settings = SettingsBackup(sex = legacy)), "u"))
            assertEquals(expected, result.data.settings?.profile?.sex, legacy)
            val settings = result.documents.single { it.path.endsWith("/${CollectionNames.SETTINGS}/app") }.data
            assertEquals(expected.wire, (settings["profile"] as Map<*, *>)["sex"], legacy)
        }
    }
}
