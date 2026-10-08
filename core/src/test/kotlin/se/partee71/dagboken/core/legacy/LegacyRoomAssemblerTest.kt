package se.partee71.dagboken.core.legacy

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.legacy.LegacyRoomSchema.Prefs
import se.partee71.dagboken.core.schema.CollectionNames

/**
 * Legacy-läsarens mappning (OMB-2): Room-raderna i v11-form och DataStore-värdena ur `room-v11.json` ger en
 * `BackupJson` som konverteraren gör till exakt `backup-v2.expected.json` – samma dokument som en Drive-backup
 * av samma data. Plus 3.x:s sätt att läsa kolumner och nycklar: båda alternativformerna, standardvärden när
 * något saknas och reservvärde med varning när något inte går att läsa.
 */
class LegacyRoomAssemblerTest {

    private val fixture = LegacyFixtures.room()
    private val assembly = LegacyRoomAssembler.assemble(fixture.tables, fixture.preferences, fixture.createdAt)

    /** Arvsfälten som v11 inte har (anteckningarna ligger i `notes`; receptets enda `tidpunkt` är v1) – de blir alltid tomma. */
    private val notInRoomV11 = setOf(
        "MedicinJson.anteckning", "ReceptJson.anteckning", "ReceptJson.tidpunkt", "FavoritJson.anteckning",
        "HandelseJson.anteckning", "SjukdomsEpisodJson.anteckning", "SjukdomsIncheckningJson.anteckning",
    )

    @Test
    fun `Room-raderna och DataStore-värdena ger samma dokument som konverteraren ger för backup-v2`() {
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(assembly.backup, LegacyFixtures.UID))
        LegacyFixtures.assertMatchesExpected("backup-v2", result, BackupJsonConverter.exportedAt(assembly.backup))
        assertEquals(emptyList(), assembly.warnings, "fixturen läses utan reservvärden")
        assertEquals(2, assembly.backup.version)
        assertEquals("2026-01-15T21:00:00", assembly.backup.createdAt)
    }

    @Test
    fun `fixturen har varje kolumn i varje v11-tabell, med ett värde i minst en rad`() {
        val schema = LegacyFixtures.schemaColumns()
        assertEquals(LegacyRoomSchema.TABLES.toSet(), schema.keys, "schemat och LegacyRoomSchema listar samma tabeller")
        for ((table, columns) in schema) {
            val rows = fixture.tables.getValue(table)
            for (row in rows) assertEquals(columns.toSet(), row.keys, "$table: raden har exakt schemats kolumner")
            for (column in columns) {
                assertTrue(rows.any { it[column] != null && it[column] != "" && it[column] != 0L }, "$table.$column saknar värde i fixturen")
            }
        }
    }

    @Test
    fun `varje 3x-fält utom arvsfälten som v11 saknar får ett icke-default-värde ur raderna`() {
        val differing = LegacyFixtures.fieldsDifferingFromDefault(assembly.backup)
        assertEquals(LegacyFixtures.BACKUP_FIELDS.toSet() - notInRoomV11, differing, "fält som stannar på standardvärdet")
        assertTrue(notInRoomV11.none { it in differing }, "arvsfälten finns inte i v11 och ska vara tomma")
    }

    @Test
    fun `alternativlistorna läses i båda 3x-formerna, okända fält ignoreras och saknad nyckel ger 3x standardlista utan varning`() {
        val assembled = LegacyRoomAssembler.assemble(
            emptyMap(),
            mapOf(
                Prefs.AKTIVITET_OPTIONS to """["Promenad","Vila"]""",
                Prefs.SYMPTOM_OPTIONS to """[{"name":"Yrsel","isFavorite":true,"framtida":1},{"name":"Övrigt"}]""",
            ),
        )
        val backup = assembled.backup
        assertEquals(listOf(SymptomOptionBackup("Promenad"), SymptomOptionBackup("Vila")), backup.aktiviteterOptionsV2)
        assertEquals(listOf("Promenad", "Vila"), backup.aktiviteterOptions, "v1-listan skrivs också, som 3.x")
        assertEquals(listOf(SymptomOptionBackup("Yrsel", true), SymptomOptionBackup("Övrigt")), backup.symptomOptionsV2)
        assertEquals(LegacyDefaults.EVENT_OPTIONS.map(::SymptomOptionBackup), backup.handelseTypOptions)
        assertEquals(emptyList(), assembled.warnings)
    }

    @Test
    fun `ett värde 3x inte kunde läsa ger 3x standardvärde med en varning utan innehållet`() {
        val assembled = LegacyRoomAssembler.assemble(
            mapOf(
                LegacyRoomSchema.RECEPT to listOf(
                    mapOf("id" to "r1", "tidpunkterJson" to "HEMLIGT-inte-json", "dagarJson" to "[1,", "dosperioderJson" to """[{"id":"b"}]"""),
                ),
            ),
            mapOf(
                Prefs.AKTIVITET_OPTIONS to "HEMLIGT {",
                Prefs.SCREENING_EVENT_CONFIGS to """[{"enabled":true}]""",
                Prefs.MED_NOTIFICATION_CONFIGS to "[1,2]",
                Prefs.THEME_LIGHT_START to "sju",
            ),
        )
        val recept = assembled.backup.medicinRecipes.single()
        assertEquals(emptyList(), recept.tidpunkter)
        assertEquals(emptyList(), recept.dagar)
        assertEquals(emptyList(), recept.dosperioder)
        assertEquals(LegacyDefaults.ACTIVITY_OPTIONS.map(::SymptomOptionBackup), assembled.backup.aktiviteterOptionsV2)
        assertEquals(LegacyDefaults.SCREENING_EVENT_CONFIGS, assembled.backup.screeningEventConfigs)
        assertEquals(LegacyDefaults.MED_NOTIFICATION_CONFIGS, assembled.backup.medNotificationConfigs)
        assertEquals(LegacyDefaults.THEME_LIGHT_START, assembled.backup.settings?.themeLightStart)
        // Alternativen läses före tabellerna; varje varning nämner nyckeln eller kolumnen, aldrig värdet.
        assertEquals(
            listOf(
                Prefs.AKTIVITET_OPTIONS to "alternativlistan", "recept/r1" to "tidpunkterJson", "recept/r1" to "dagarJson", "recept/r1" to "dosperioderJson",
                Prefs.SCREENING_EVENT_CONFIGS to "listan", Prefs.MED_NOTIFICATION_CONFIGS to "listan", Prefs.THEME_LIGHT_START to "annan typ",
            ),
            assembled.warnings.map { it.path to it.message.substringBefore(" går").substringBefore(" (").substringBefore(" än").substringAfter("avkoda ").substringAfter("har en ") },
        )
        val rendered = assembled.warnings.joinToString()
        assertTrue("HEMLIGT" !in rendered && "sju" !in rendered, rendered)
    }

    @Test
    fun `en enhet utan sparade inställningar ger 3x standardvärden, och konverteras utan stopp`() {
        val assembled = LegacyRoomAssembler.assemble(emptyMap(), emptyMap())
        val backup = assembled.backup
        assertEquals(
            SettingsBackup(
                medsNotificationsEnabled = false, themeMode = "auto", themeLightStart = 7, themeDarkStart = 21,
                isDarkTheme = true, dynamicColor = true, birthYear = null, sex = LegacyDefaults.SEX_UNSPECIFIED,
            ),
            backup.settings,
        )
        assertEquals("09:00", backup.periodReminderTime)
        assertNull(backup.sheetsConfig)
        assertEquals(listOf("08:00", "12:00", "17:00", "21:00"), backup.screeningEventConfigs?.map { it.time })
        assertEquals(listOf("Morgon", "Förmiddag", "Lunch", "Eftermiddag", "Kväll", "Natt"), backup.medNotificationConfigs?.map { it.tidpunkt })
        assertEquals(listOf("07:00", "10:00", "12:00", "15:00", "19:00", "22:00"), backup.medNotificationConfigs?.map { it.time })
        assertEquals(emptyList(), assembled.warnings)
        val result = assertIs<ConversionResult.Converted>(BackupJsonConverter.convert(backup, "u"))
        assertEquals(mapOf(CollectionNames.USERS to 1, CollectionNames.SETTINGS to 1, CollectionNames.OPTIONS to 22), result.report.counts)
    }

    @Test
    fun `ett blankt temaläge räknas som saknat med varning, och en talkolumn med fel typ läses som 0 med varning`() {
        val assembled = LegacyRoomAssembler.assemble(
            mapOf(LegacyRoomSchema.FAVORITER to listOf(mapOf("id" to "f1", "minTidMellan" to "sex", "isFavorite" to 1L))),
            mapOf(Prefs.THEME_MODE to " "),
        )
        assertEquals(LegacyDefaults.THEME_MODE, assembled.backup.settings?.themeMode)
        assertEquals(0, assembled.backup.medicinFavoriter.single().minTidMellan)
        assertTrue(assembled.backup.medicinFavoriter.single().isFavorite)
        // Raderna läses före inställningarna (argumentordningen i BackupJson).
        assertEquals(
            listOf("favoriter/f1" to "minTidMellan har en annan typ", Prefs.THEME_MODE to "tomt värde"),
            assembled.warnings.map { it.path to it.message.substringBefore(" –").substringBefore(" än") },
        )
    }

    @Test
    fun `REAL i en talkolumn läses som 0 med varning, och en BLOB i en textkolumn som tom med varning`() {
        val assembled = LegacyRoomAssembler.assemble(
            mapOf(LegacyRoomSchema.FAVORITER to listOf(mapOf("id" to "f1", "namn" to byteArrayOf(1, 2, 3), "minTidMellan" to 3.5, "isFavorite" to 1L, "maxDoserPerDag" to 2L))),
            emptyMap(),
        )
        val favorite = assembled.backup.medicinFavoriter.single()
        assertEquals(0, favorite.minTidMellan)
        assertEquals("", favorite.namn)
        assertEquals(2, favorite.maxDoserPerDag, "INTEGER läses som vanligt")
        assertEquals(
            listOf("favoriter/f1" to "namn är binär (BLOB)", "favoriter/f1" to "minTidMellan har en annan typ"),
            assembled.warnings.map { it.path to it.message.substringBefore(" –").substringBefore(" än") },
        )
    }

    @Test
    fun `kön läses som 3x Sex fromStorageKey - okänt blir ej angivet`() {
        assertEquals("kvinna", LegacyRoomAssembler.assemble(emptyMap(), mapOf(Prefs.SEX to "kvinna")).backup.settings?.sex)
        assertEquals(LegacyDefaults.SEX_UNSPECIFIED, LegacyRoomAssembler.assemble(emptyMap(), mapOf(Prefs.SEX to "okänt")).backup.settings?.sex)
    }

    @Test
    fun `en sjunde medicinpåminnelse får inget tidpunktsnamn, som 3x BackupAssembler`() {
        val raw = (1..7).joinToString(",", "[", "]") { """{"enabled":true,"time":"0$it:00"}""" }
        val configs = LegacyRoomAssembler.assemble(emptyMap(), mapOf(Prefs.MED_NOTIFICATION_CONFIGS to raw)).backup.medNotificationConfigs!!
        assertEquals(listOf("Morgon", "Förmiddag", "Lunch", "Eftermiddag", "Kväll", "Natt", ""), configs.map { it.tidpunkt })
    }

    @Test
    fun `createdAt skrivs som 3x LocalDateTime i Europe Stockholm`() {
        assertEquals("2026-01-15T21:00:00", LegacyRoomAssembler.createdAt(1_768_507_200_000)) // 2026-01-15T20:00:00Z
        assertEquals("2026-07-01T14:30:05", LegacyRoomAssembler.createdAt(1_782_909_005_000)) // sommartid, sekunder kvar, millisekunder bort
    }
}
