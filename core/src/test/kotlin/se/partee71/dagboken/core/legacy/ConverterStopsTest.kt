package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.TextLimits

/**
 * Varje fall där konverteraren **stoppar** i stället för att kapa, avrunda eller hoppa över (OMB-3):
 * rules-gränserna, värden utan plats i 4.0 och data som 3.x inte heller kunde läsa. Rapporten nämner
 * aldrig innehållet.
 */
class ConverterStopsTest {

    private fun problems(backup: BackupJson): List<Problem> = assertIs<ConversionResult.Stopped>(BackupJsonConverter.convert(backup, "u")).report.problems

    /** Ett enda stopp med given sökväg och fält, vars skäl innehåller [reason]. */
    private fun assertStops(backup: BackupJson, path: String, field: String, reason: String) {
        val problem = problems(backup).single()
        assertEquals(path to field, problem.path to problem.field)
        assertTrue(reason in problem.reason, "skälet '${problem.reason}' ska nämna '$reason'")
    }

    private fun activity(id: String = "a1", symptom: String = "", energy: Int = 0, spentTime: Int? = null, datum: String = "2026-01-15", tid: String = "09:00") =
        BackupJson(aktiviteter = listOf(AktivitetJson(id = id, datum = datum, tid = tid, aktivitet = "Promenad", energy = energy, symptom = symptom, spentTime = spentTime)))

    private fun screening(aterhamtande: Boolean = false, energitjuv: Boolean = false, spentTime: Int? = null, energy: Int = 5) =
        BackupJson(aktiviteter = listOf(AktivitetJson(id = "s1", datum = "2026-01-15", tid = "08:00", aktivitet = "Efter frukost", type = "screening", energy = energy, aterhamtande = aterhamtande, energitjuv = energitjuv, spentTime = spentTime)))

    private fun dose(id: String = "m1", datum: String = "2026-01-15", tid: String = "07:00", tidpunkt: String = "Morgon", tagenTid: String? = null, anteckning: String = "") =
        BackupJson(mediciner = listOf(MedicinJson(id = id, datum = datum, tid = tid, tidpunkt = tidpunkt, tagenTid = tagenTid, anteckning = anteckning)))

    @Test
    fun `för lång anteckning stoppar och rapporten anger längden, aldrig texten`() {
        val text = "HEMLIGT ".repeat(TextLimits.LONG / 8 + 1)
        val result = assertIs<ConversionResult.Stopped>(BackupJsonConverter.convert(dose(anteckning = text), "u"))
        val problem = result.report.problems.single()
        assertEquals(Problem("doses/m1", "note", "för lång text: ${text.length} tecken (högst ${TextLimits.LONG})"), problem)
        assertFalse(result.report.render().contains("HEMLIGT"))
        assertTrue("Stopp: 1 fel" in result.report.render())
    }

    @Test
    fun `för långt namn stoppar`() = assertStops(
        BackupJson(medicinFavoriter = listOf(FavoritJson(id = "f1", tidpunkt = "Vid behov", namn = "x".repeat(TextLimits.SHORT + 1)))),
        "prnMedicines/f1", "name", "för lång text: ${TextLimits.SHORT + 1}",
    )

    @Test
    fun `energi utanför intervallet stoppar - screening 0 till 10, aktivitet -10 till 10`() {
        assertStops(screening(energy = 11), "screenings/s1", "energy", "utanför intervallet 0..10: 11")
        assertStops(screening(energy = -1), "screenings/s1", "energy", "utanför intervallet 0..10: -1")
        assertStops(activity(energy = -11), "activities/a1", "energy", "utanför intervallet -10..10: -11")
    }

    @Test
    fun `screening med aterhamtande, energitjuv eller spentTime har ingen plats och stoppar - utom spentTime 0 som är ej angivet`() {
        assertStops(screening(aterhamtande = true), "screenings/s1", "aterhamtande", "ingen plats")
        assertStops(screening(energitjuv = true), "screenings/s1", "energitjuv", "ingen plats")
        assertStops(screening(spentTime = 30), "screenings/s1", "spentTime", "30")
        // 3.x före #157 sparade spentTime = 0 på varje screening (formuläret saknar fältet): ej angivet → saknas.
        val zero = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(screening(spentTime = 0), "u"))
        assertEquals(1, zero.data.screenings.size)
        assertTrue(zero.documents.single { it.path.contains("/screenings/") }.data.keys.none { it == "minutes" || it == "spentTime" })
        assertEquals(emptyList(), zero.report.warnings)
    }

    @Test
    fun `fler än 50 symptom stoppar, 50 går`() {
        val fifty = (1..50).joinToString(",") { "Symptom $it:1" }
        assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(activity(symptom = fifty), "u"))
        assertStops(activity(symptom = "$fifty,Symptom 51:1"), "activities/a1", "symptoms", "för många element: 51 (högst ${DocumentRules.MAX_SYMPTOMS})")
    }

    @Test
    fun `symptompoäng utanför 0 till 10 stoppar med elementets plats`() {
        assertStops(activity(symptom = "Huvudvärk:3,Yrsel:11"), "activities/a1", "symptoms[1].score", "utanför intervallet 0..10: 11")
    }

    @Test
    fun `en poäng utan namn stoppar - delar utan poäng stoppar inte längre (BackupJsonConverterTest)`() {
        assertStops(activity(symptom = "Huvudvärk:2,:3"), "activities/a1", "symptom[1]", "saknar namn")
        for (raw in listOf("Huvudvärk", "Huvudvärk:tre", "Övrigt (yrsel, kl 14:00):2", "Huvudvärk,Yrsel")) {
            assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(activity(symptom = raw), "u"), raw)
        }
    }

    @Test
    fun `okänd tidpunkt stoppar - dos, recept och vid behov-medicin`() {
        assertStops(dose(tidpunkt = "Brunch"), "doses/m1", "tidpunkt", "okänd tidpunkt okänt värde (6 tecken)")
        assertStops(dose(tidpunkt = ""), "doses/m1", "tidpunkt", "okänd tidpunkt okänt värde (0 tecken)")
        assertStops(BackupJson(medicinRecipes = listOf(ReceptJson(id = "r1", tidpunkter = listOf("Morgon", "Brunch")))), "prescriptions/r1", "tidpunkter", "(6 tecken)")
        assertStops(BackupJson(medicinFavoriter = listOf(FavoritJson(id = "f1", tidpunkt = ""))), "prnMedicines/f1", "tidpunkt", "okänd tidpunkt")
    }

    @Test
    fun `ogiltigt datum eller klockslag stoppar - även ett datum som inte finns`() {
        assertStops(dose(datum = "2026-02-30"), "doses/m1", "datum", "ogiltigt datum")
        assertStops(dose(datum = "15/1 2026"), "doses/m1", "datum", "ogiltigt datum")
        assertStops(dose(tid = "7:00"), "doses/m1", "tid", "ogiltigt klockslag")
        assertStops(dose(tagenTid = "07:04:30"), "doses/m1", "tagenTid", "ogiltigt klockslag")
        assertStops(BackupJson(sjukdomsepisoder = listOf(SjukdomsEpisodJson(id = "e1", slutDatum = "2026-13-01"))), "illnessEpisodes/e1", "slutDatum", "ogiltigt datum")
    }

    @Test
    fun `tagenTid utan datum kan inte placeras och stoppar`() =
        assertStops(dose(datum = "", tagenTid = "07:04"), "doses/m1", "tagenTid", "datum saknas")

    @Test
    fun `incheckning utan sin episod stoppar - det finns ingen sökväg`() = assertStops(
        BackupJson(sjukdomsIncheckningar = listOf(SjukdomsIncheckningJson(id = "i1", episodId = "finns-inte"))),
        "illnessEpisodes/finns-inte/checkins/i1", "episodId", "episoden finns inte",
    )

    @Test
    fun `två poster med samma id stoppar - den andra skulle skriva över den första`() {
        val backup = BackupJson(mediciner = listOf(MedicinJson(id = "m1", tidpunkt = "Morgon"), MedicinJson(id = "m1", tidpunkt = "Kväll")))
        assertStops(backup, "doses/m1", "id", "dubblett")
    }

    @Test
    fun `ett id som Firestore inte godtar stoppar`() {
        // Ett ogiltigt id kan vara vad som helst – rapporten anger längden, inte id:t.
        assertStops(dose(id = "a/b"), "doses/(ogiltigt id, 3 tecken)", "id", "ogiltigt dokument-id (3 tecken)")
        assertStops(dose(id = ""), "doses/(ogiltigt id, 0 tecken)", "id", "ogiltigt dokument-id")
        assertStops(dose(id = ".."), "doses/(ogiltigt id, 2 tecken)", "id", "ogiltigt dokument-id")
    }

    @Test
    fun `veckodag utanför 0 till 6 och negativt intervall stoppar`() {
        assertStops(BackupJson(medicinRecipes = listOf(ReceptJson(id = "r1", dagar = listOf(1, 7)))), "prescriptions/r1", "dagar", "ingen veckodag: 7")
        assertStops(BackupJson(medicinRecipes = listOf(ReceptJson(id = "r1", intervalDagar = -1))), "prescriptions/r1", "schedule.intervalDays", "under 0: -1")
    }

    @Test
    fun `negativa tal där rules kräver minst 0 stoppar`() {
        assertStops(activity(spentTime = -5), "activities/a1", "minutes", "under 0: -5")
        assertStops(BackupJson(medicinFavoriter = listOf(FavoritJson(id = "f1", tidpunkt = "Vid behov", minTidMellan = -1))), "prnMedicines/f1", "minHoursBetween", "under 0: -1")
        assertStops(BackupJson(handelser = listOf(HandelseJson(id = "h1", typ = "Yrsel", varaktighetMinuter = -1))), "events/h1", "durationMinutes", "under 0: -1")
        assertStops(BackupJson(handelser = listOf(HandelseJson(id = "h1", typ = "Yrsel", svarighetsgrad = 11))), "events/h1", "severity", "utanför intervallet 0..10: 11")
    }

    @Test
    fun `inställningar - okänt tema, kön eller timme och påminnelserader utan plats stoppar`() {
        val path = "settings/app"
        assertStops(BackupJson(settings = SettingsBackup(themeMode = "sepia")), path, "themeMode", "okänt värde (5 tecken)")
        assertStops(BackupJson(settings = SettingsBackup(sex = "annat")), path, "sex", "okänt värde (5 tecken)")
        assertStops(BackupJson(settings = SettingsBackup(themeLightStart = 24)), path, "theme.lightStartHour", "utanför intervallet 0..23: 24")
        assertStops(BackupJson(periodReminderTime = "9:00"), path, "periodReminderTime", "ogiltigt klockslag")
        assertStops(BackupJson(screeningEventConfigs = List(5) { ScreeningEventConfigJson(true, "08:00") }), path, "screeningEventConfigs", "fler tillfällen än de fyra: 5")
        assertStops(BackupJson(screeningEventConfigs = listOf(ScreeningEventConfigJson(true, "8:00"))), path, "screeningEventConfigs[0].time", "ogiltigt klockslag")
        assertStops(BackupJson(medNotificationConfigs = listOf(MedNotificationConfigJson("Vid behov", true, "12:00"))), path, "medNotificationConfigs[0].tidpunkt", "okänd tidpunkt okänt värde (9 tecken)")
        assertStops(BackupJson(medNotificationConfigs = listOf(MedNotificationConfigJson("Morgon", true, "25:00"))), path, "medNotificationConfigs[0].time", "ogiltigt klockslag")
    }

    @Test
    fun `rapporten innehåller varken alternativens namn eller slug, inte heller råa enum- eller tidpunktsvärden`() {
        val backup = BackupJson(
            aktiviteterOptionsV2 = listOf(SymptomOptionBackup("Hemligt Besök"), SymptomOptionBackup("Hemligt Besök", isFavorite = true)),
            symptomOptionsV2 = listOf(SymptomOptionBackup("Hemlig Diagnos ".repeat(20))),
            mediciner = listOf(MedicinJson(id = "m1", tidpunkt = "Hemligtid", datum = "2026-01-15")),
            settings = SettingsBackup(themeMode = "hemligtema", sex = "hemligkon"),
        )
        val result = assertIs<ConversionResult.Stopped>(BackupJsonConverter.convert(backup, "u"))
        val rendered = result.report.render()
        for (secret in listOf("Hemlig", "hemlig", "Besök", "besok", "Diagnos", "diagnos", "Hemligtid", "hemligtema", "hemligkon")) {
            assertFalse(secret in rendered, "rapporten får inte innehålla '$secret':\n$rendered")
        }
        assertEquals(listOf(Warning("options/activity#1", "namnet finns två gånger i activity-listan – det första gäller")), result.report.warnings)
        assertEquals(
            listOf(
                Problem("doses/m1", "tidpunkt", "okänd tidpunkt okänt värde (9 tecken)"),
                Problem("options/symptom#0", "name", "för lång text: 300 tecken (högst ${TextLimits.SHORT})"),
                Problem("settings/app", "themeMode", "okänt värde (10 tecken)"),
                Problem("settings/app", "sex", "okänt värde (9 tecken)"),
            ),
            result.report.problems.sortedBy { it.path },
        )
    }

    @Test
    fun `anteckningar med text men utan target eller id räknas i rapporten - inte tyst bort`() {
        val backup = BackupJson(
            notes = listOf(NoteJson(target = "", entityId = "x", text = "Hemlig text"), NoteJson(target = "EVENT", entityId = "", text = "Annan"), NoteJson(target = "", entityId = "", text = "")),
        )
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"))
        assertEquals(listOf(Warning("notes", "2 anteckning(ar) utan target eller id – kan inte placeras")), result.report.warnings)
        assertFalse("Hemlig" in result.report.render())
    }

    @Test
    fun `okänt target och ogiltigt id i en anteckning rapporteras bara med längd - aldrig värdet`() {
        val backup = BackupJson(
            notes = listOf(
                NoteJson(target = "HEMLIGT", entityId = "a/b", text = "Dold"),
                NoteJson(target = "HEMLIGT", entityId = "a/b", text = "Dold igen"),
            ),
        )
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"))
        assertEquals(
            listOf(
                Warning("notes", "två anteckningar för okänt target (7 tecken) (ogiltigt id, 3 tecken) med olika text – den första gäller"),
                Warning("notes", "1 anteckning(ar) med target okänt target (7 tecken) utan sin post – kan inte placeras"),
            ),
            result.report.warnings,
        )
        val rendered = result.report.render()
        assertFalse("HEMLIGT" in rendered || "a/b" in rendered || "Dold" in rendered, rendered)
    }

    @Test
    fun `tagenTid i sommartidsbytet varnas i rapporten - luckan och överlappningen`() {
        val backup = BackupJson(
            mediciner = listOf(
                MedicinJson(id = "m1", datum = "2026-03-29", tid = "02:30", tidpunkt = "Natt", tagen = true, tagenTid = "02:30", timestamp = ""),
                MedicinJson(id = "m2", datum = "2026-10-25", tid = "01:00", tidpunkt = "Natt", tagen = true, tagenTid = "02:30"),
                MedicinJson(id = "m3", datum = "2026-10-25", tid = "01:00", tidpunkt = "Natt", tagen = true, tagenTid = "03:30"),
            ),
        )
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"))
        assertEquals(
            listOf(
                Warning("doses/m1", "tagenTid ligger i luckan vid sommartidsbytet – flyttat fram med luckans längd"),
                Warning("doses/m1", "timestamp ligger i luckan vid sommartidsbytet – flyttat fram med luckans längd"),
                Warning("doses/m2", "tagenTid ligger i överlappningen vid sommartidsbytet – första förekomsten (sommartid) gäller"),
            ),
            result.report.warnings,
        )
        val doses = result.data.doses.associateBy { it.id }
        assertEquals(kotlin.time.Instant.parse("2026-03-29T01:30:00Z"), doses.getValue("m1").takenAt)
        assertEquals(kotlin.time.Instant.parse("2026-10-25T00:30:00Z"), doses.getValue("m2").takenAt)
        assertEquals(kotlin.time.Instant.parse("2026-10-25T02:30:00Z"), doses.getValue("m3").takenAt)
    }

    @Test
    fun `alla fel samlas i en rapport - inte bara det första`() {
        val backup = BackupJson(
            mediciner = listOf(MedicinJson(id = "m1", tidpunkt = "Brunch", datum = "igår")),
            aktiviteter = listOf(AktivitetJson(id = "a1", energy = 99, aktivitet = "Promenad")),
        )
        val problems = problems(backup)
        assertEquals(listOf("doses/m1" to "datum", "doses/m1" to "tidpunkt", "activities/a1" to "energy"), problems.map { it.path to it.field })
    }

    @Test
    fun `ett stopp lämnar inte ut några dokument, men räknar dem i rapporten`() {
        val report = assertIs<ConversionResult.Stopped>(BackupJsonConverter.convert(screening(energy = 11), "u")).report
        assertEquals(mapOf("users" to 1, "options" to 22, "screenings" to 1), report.counts)
        assertTrue(report.stopped)
    }
}
