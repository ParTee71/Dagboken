package se.partee71.dagboken.data.migration

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.data.room.entities.NoteEntity
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning

/**
 * Regel 1: ingen användardata får försvinna i en backup-rundtur.
 *
 * Testet går hela vägen domän → [BackupAssembler] → JSON-text → [BackupMapper] → domän
 * och jämför fält för fält. Tidigare testades bara importsidan, från en handskriven
 * [BackupJson] — ett fält som glömdes bort i exportmappningen kunde alltså försvinna
 * utan att något test blev rött.
 */
class BackupRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Alla fält satta till värden skilda från sina standardvärden, så ett fält som
    // tappas på vägen inte råkar se rätt ut ändå.
    private val aktivitet = Aktivitet(
        id = "a1", timestamp = "2026-01-15T09:00:00Z", datum = "2026-01-15", tid = "09:00",
        aktivitet = "Promenad", energy = 7, stress = 3, somatiska = 2,
        symptom = "Huvudvärk:4", aterhamtande = true, energitjuv = false,
        type = "aktivitet", spentTime = 45,
    )

    private val screening = aktivitet.copy(id = "a2", aktivitet = "Lunch", type = "screening")

    private val medicin = Medicin(
        id = "m1", timestamp = "2026-01-15T07:00:00Z", datum = "2026-01-15", tid = "07:00",
        namn = "Metformin", dos = "500", enhet = "mg", tidpunkt = "Morgon",
        tagen = true, receptId = "r1", skipped = false, tagenTid = "07:04",
    )

    private val dosperiod = Dosperiod(
        id = "d1", startDatum = "2026-02-01", slutDatum = "2026-02-07", dos = "250", enhet = "mg",
    )

    private val recept = Recept(
        id = "r1", namn = "Metformin", dos = "500", enhet = "mg",
        tidpunkter = listOf("Morgon", "Kväll"), upprepning = "intervall",
        dagar = listOf(0, 2, 4), intervalDagar = 3, aktiv = true, skapad = "2026-01-01",
        startDatum = "2026-01-10", slutDatum = "2026-03-01", dosperioder = listOf(dosperiod),
    )

    private val favorit = Favorit(
        id = "f1", namn = "Ibuprofen", dos = "400", enhet = "mg", tidpunkt = "Vid behov",
        minTidMellan = 6, dispenseringsTid = "08:00", maxDoserPerDag = 3, isFavorite = true,
    )

    private val handelse = Handelse(
        id = "h1", timestamp = "2026-01-15T13:00:00Z", datum = "2026-01-15", tid = "13:00",
        typ = "Ögonmigrän", svarighetsgrad = 6, varaktighetMinuter = 30,
        triggers = "Skärmtid", atgarder = "Vila",
    )

    private val episod = SjukdomsEpisod(
        id = "e1", typ = "Förkylning", startDatum = "2026-01-10", slutDatum = "2026-01-18",
        timestamp = 1_760_000_000_000,
    )

    private val incheckning = SjukdomsIncheckning(
        id = "i1", episodId = "e1", datum = "2026-01-11", tid = "20:00",
        svarighetsgrad = 5, symptom = "Hosta:3", somatiska = 4, timestamp = 1_760_000_100_000,
    )

    private val notes = listOf(
        NoteEntity(target = "MEDICATION", entityId = "m1", text = "Med mat"),
        NoteEntity(target = "SJUKDOM_EPISOD", entityId = "e1", text = "Feber första dagen"),
    )

    private val settings = SettingsBackup(
        medsNotificationsEnabled = true,
        themeMode = "dark",
        themeLightStart = 6,
        themeDarkStart = 22,
        isDarkTheme = false,
        dynamicColor = false,
        birthYear = 1971,
        sex = "man",
    )

    private fun assemble(): BackupJson = BackupAssembler.assemble(
        createdAt             = "2026-01-15T21:00:00",
        aktiviteter           = listOf(aktivitet, screening),
        mediciner             = listOf(medicin),
        recept                = listOf(recept),
        favoriter             = listOf(favorit),
        handelser             = listOf(handelse),
        episoder              = listOf(episod),
        incheckningar         = listOf(incheckning),
        notes                 = notes,
        aktivitetOptions      = listOf(SymptomOption("Promenad", isFavorite = true), SymptomOption("Jobb")),
        symptomOptions        = listOf(SymptomOption("Huvudvärk", isFavorite = true)),
        handelseTypOptions    = listOf(SymptomOption("Ögonmigrän")),
        screeningEventConfigs = listOf(ScreeningEventConfig(enabled = true, time = "08:30")),
        sheetsConfig          = "https://example.test/sheet",
        periodReminderTime    = "10:15",
        settings              = settings,
    )

    /** Skriver ut och läser tillbaka, precis som Drive-vägen gör. */
    private fun roundTrip(backup: BackupJson): BackupJson =
        json.decodeFromString(json.encodeToString(BackupJson.serializer(), backup))

    @Test fun `aktiviteter survive the round trip unchanged`() {
        val restored = BackupMapper.toAktiviteter(roundTrip(assemble()))
        assertEquals(listOf(aktivitet, screening), restored)
    }

    @Test fun `mediciner survive the round trip unchanged`() {
        val restored = BackupMapper.toMediciner(roundTrip(assemble()))
        assertEquals(listOf(medicin), restored)
    }

    @Test fun `recept including dosperioder survive the round trip unchanged`() {
        val restored = BackupMapper.toRecept(roundTrip(assemble()))
        assertEquals(listOf(recept), restored)
        assertEquals(listOf(dosperiod), restored.single().dosperioder)
    }

    @Test fun `favoriter survive the round trip unchanged`() {
        assertEquals(listOf(favorit), BackupMapper.toFavoriter(roundTrip(assemble())))
    }

    @Test fun `handelser survive the round trip unchanged`() {
        assertEquals(listOf(handelse), BackupMapper.toHandelser(roundTrip(assemble())))
    }

    @Test fun `sjukdomsepisoder and incheckningar survive the round trip unchanged`() {
        val restored = roundTrip(assemble())
        assertEquals(listOf(episod), BackupMapper.toSjukdomsEpisoder(restored))
        assertEquals(listOf(incheckning), BackupMapper.toSjukdomsIncheckningar(restored))
    }

    @Test fun `notes survive the round trip unchanged`() {
        assertEquals(notes, BackupMapper.toNotes(roundTrip(assemble())))
    }

    @Test fun `options and settings survive the round trip unchanged`() {
        val restored = roundTrip(assemble())

        assertEquals(
            listOf(SymptomOptionBackup("Promenad", true), SymptomOptionBackup("Jobb", false)),
            restored.aktiviteterOptionsV2,
        )
        assertEquals(listOf(SymptomOptionBackup("Huvudvärk", true)), restored.symptomOptionsV2)
        assertEquals(listOf(SymptomOptionBackup("Ögonmigrän", false)), restored.handelseTypOptions)
        assertEquals(listOf(ScreeningEventConfigJson(true, "08:30")), restored.screeningEventConfigs)
        assertEquals("https://example.test/sheet", restored.sheetsConfig)
        assertEquals("10:15", restored.periodReminderTime)
        assertEquals(settings, restored.settings)
    }

    @Test fun `profile settings survive the round trip`() {
        // HLS-11: födelseår och kön styr sömnkvalitetens åldersnormer. Tappas de vid
        // enhetsbyte mäts nätterna plötsligt mot fel norm, utan att något syns.
        val restored = roundTrip(assemble()).settings!!
        assertEquals(1971, restored.birthYear)
        assertEquals("man", restored.sex)
    }

    @Test fun `a backup written before the profile fields still decodes`() {
        val encoded = json.encodeToString(BackupJson.serializer(), assemble())
            .replace(Regex(",\"birthYear\":\\d+"), "")
            .replace(Regex(",\"sex\":\"[^\"]*\""), "")
        val restored = json.decodeFromString(BackupJson.serializer(), encoded)
        assertEquals(null, restored.settings?.birthYear)
        assertEquals(null, restored.settings?.sex)
    }

    @Test fun `v1 options list is still written so older app versions can read the backup`() {
        val restored = roundTrip(assemble())
        assertEquals(listOf("Promenad", "Jobb"), restored.aktiviteterOptions)
        assertEquals(listOf("Huvudvärk"), restored.symptomOptions)
    }

    @Test fun `blank sheetsConfig is written as null rather than an empty string`() {
        val backup = BackupAssembler.assemble(
            createdAt = "2026-01-15T21:00:00",
            aktiviteter = emptyList(), mediciner = emptyList(), recept = emptyList(),
            favoriter = emptyList(), handelser = emptyList(), episoder = emptyList(),
            incheckningar = emptyList(), notes = emptyList(),
            aktivitetOptions = emptyList(), symptomOptions = emptyList(),
            handelseTypOptions = emptyList(), screeningEventConfigs = emptyList(),
            sheetsConfig = "  ", periodReminderTime = "09:00", settings = SettingsBackup(),
        )
        assertEquals(null, roundTrip(backup).sheetsConfig)
    }

    /**
     * En backup utan `settings` (skriven före BCK-10) ska fortfarande gå att läsa, och
     * lämna inställningarna orörda i stället för att nollställa dem.
     */
    @Test fun `older backups without settings still decode`() {
        val withoutSettings = assemble().let { json.encodeToString(BackupJson.serializer(), it) }
            .replace(Regex(",\"settings\":\\{[^}]*\\}"), "")
        val restored = json.decodeFromString<BackupJson>(withoutSettings)

        assertEquals(null, restored.settings)
        assertEquals(listOf(medicin), BackupMapper.toMediciner(restored))
    }

    @Test fun `format version is written`() {
        assertEquals(BackupAssembler.BACKUP_FORMAT_VERSION, roundTrip(assemble()).version)
    }

    /**
     * Vaktpost mot fält som tappas ur själva backupformatet: varje persisterat fältnamn
     * ska finnas i den utskrivna JSON-texten. Kodas med `encodeDefaults = true` — utan
     * det utelämnar kotlinx fält vars värde råkar vara lika med standardvärdet (t.ex.
     * `skipped = false`), och testet skulle då falla på fullt korrekt data.
     *
     * Att varje fält också *fylls i* av mappningen garanteras av rundturstesterna ovan,
     * som jämför hela domänobjekt fält för fält.
     */
    @Test fun `every persisted field name appears in the encoded backup`() {
        val encoded = Json { encodeDefaults = true }
            .encodeToString(BackupJson.serializer(), assemble())

        val expectedFields = listOf(
            // Aktivitet
            "aterhamtande", "energitjuv", "spentTime", "somatiska",
            // Medicin
            "tagenTid", "skipped", "receptId", "tidpunkt",
            // Recept
            "startDatum", "slutDatum", "dosperioder", "intervalDagar", "upprepning",
            // Favorit
            "dispenseringsTid", "maxDoserPerDag", "isFavorite", "minTidMellan",
            // Handelse
            "varaktighetMinuter", "triggers", "atgarder",
            // Sjukdom
            "episodId", "svarighetsgrad",
            // Notes + inställningar
            "notes", "entityId", "settings", "medsNotificationsEnabled",
            // Profil (HLS-11) — utan dessa tappas åldersnormerna vid enhetsbyte
            "birthYear", "sex",
        )
        expectedFields.forEach { field ->
            assertTrue("Fältet '$field' saknas i den utskrivna backupen", encoded.contains("\"$field\""))
        }
        assertNotNull(roundTrip(assemble()).settings)
    }
}
