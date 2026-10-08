package se.partee71.dagboken.core.legacy

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.partee71.dagboken.core.legacy.LegacyRoomSchema.Prefs
import se.partee71.dagboken.core.model.Slot

/** En tabellrad som SQLite ger den: kolumnnamn → TEXT som `String`, INTEGER som `Long`, NULL som `null`. */
typealias Row = Map<String, Any?>

/** [backup] i exakt 3.x `BackupAssembler`-form, och det som lästes med 3.x:s reservvärde i stället ([warnings], utan innehåll). */
data class LegacyAssembly(val backup: BackupJson, val warnings: List<Warning>)

/**
 * Room-raderna (v11) och DataStore-värdena från en 3.x-enhet → [BackupJson], fält för fält som 3.x
 * `BackupAssembler.assemble` skrev sin backup och som 3.x läste sina kolumner och nycklar (OMB-2). Ren
 * funktion: `:app` läser bara SQL och DataStore och lämnar raderna hit, så att **samma** konverterare
 * ([BackupJsonConverter]) ger samma dokument för en Room-fil som för en Drive-backup av samma data.
 *
 * - JSON-kolumnerna på receptet (`tidpunkterJson`, `dagarJson`, `dosperioderJson`) och DataStore-värdena
 *   läses som 3.x gjorde (`Json { ignoreUnknownKeys = true }`): alternativlistorna i både den äldre listformen
 *   (`["Promenad"]`) och som `SymptomOption`; ett värde 3.x inte kunde läsa ger 3.x:s standardvärde (som
 *   3.x visade det) med en [Warning] – posten var redan oläslig i 3.x, och ett stopp hade ingen åtgärd.
 * - Arvsfälten `anteckning` finns inte i v11 (anteckningarna ligger i `notes`) och blir tomma, som i 3.x:s backup.
 * - Saknad DataStore-nyckel → 3.x:s standardvärde ([LegacyDefaults]), så inställningsdokumentet får samma
 *   fält som en 3.x-backup av samma enhet.
 */
object LegacyRoomAssembler {
    /** 3.x `BackupAssembler.BACKUP_FORMAT_VERSION`. */
    const val FORMAT_VERSION = 2

    /**
     * [tables] är raderna per tabellnamn ([LegacyRoomSchema.TABLES]); [preferences] DataStore-nycklarna med
     * sina råa värden; [createdAt] backupfilens metadata (3.x lokal tid utan zon, [createdAt]).
     */
    fun assemble(tables: Map<String, List<Row>>, preferences: Map<String, Any?>, createdAt: String = ""): LegacyAssembly =
        Assembly(tables, preferences).run(createdAt)

    /**
     * Antal poster per Room-tabell i en [BackupJson] – det kopian av 3.x-datan jämförs med mot raderna som lästes
     * (OMB-8): samma tabellnamn som [LegacyRoomSchema.TABLES], så att `tables.mapValues { it.size }` ska vara lika.
     */
    fun tableCounts(backup: BackupJson): Map<String, Int> = mapOf(
        LegacyRoomSchema.AKTIVITETER to backup.aktiviteter.size,
        LegacyRoomSchema.MEDICINER to backup.mediciner.size,
        LegacyRoomSchema.RECEPT to backup.medicinRecipes.size,
        LegacyRoomSchema.FAVORITER to backup.medicinFavoriter.size,
        LegacyRoomSchema.HEALTH_EVENTS to backup.handelser.size,
        LegacyRoomSchema.NOTES to backup.notes.size,
        LegacyRoomSchema.SJUKDOMSEPISODER to backup.sjukdomsepisoder.size,
        LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR to backup.sjukdomsIncheckningar.size,
    )

    /** `createdAt` som 3.x skrev det (`LocalDateTime.now()` i ISO utan zon) för ett ögonblick i Europe/Stockholm. */
    fun createdAt(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(LegacyTime.ZONE.id)).toLocalDateTime().withNano(0)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

private class Assembly(private val tables: Map<String, List<Row>>, private val prefs: Map<String, Any?>) {
    private val warnings = mutableListOf<Warning>()

    fun run(createdAt: String): LegacyAssembly {
        val activityOptions = options(Prefs.AKTIVITET_OPTIONS, LegacyDefaults.ACTIVITY_OPTIONS)
        val symptomOptions = options(Prefs.SYMPTOM_OPTIONS, LegacyDefaults.SYMPTOM_OPTIONS)
        val eventOptions = options(Prefs.HANDELSE_TYP_OPTIONS, LegacyDefaults.EVENT_OPTIONS)
        val backup = BackupJson(
            version = LegacyRoomAssembler.FORMAT_VERSION,
            createdAt = createdAt,
            aktiviteter = rows(LegacyRoomSchema.AKTIVITETER).map(::aktivitet),
            mediciner = rows(LegacyRoomSchema.MEDICINER).map(::medicin),
            medicinRecipes = rows(LegacyRoomSchema.RECEPT).map(::recept),
            medicinFavoriter = rows(LegacyRoomSchema.FAVORITER).map(::favorit),
            // V1-listorna skrevs fortfarande av 3.x så att en äldre app kunde läsa backupen.
            aktiviteterOptions = activityOptions.map { it.name },
            symptomOptions = symptomOptions.map { it.name },
            aktiviteterOptionsV2 = activityOptions,
            symptomOptionsV2 = symptomOptions,
            sjukdomsepisoder = rows(LegacyRoomSchema.SJUKDOMSEPISODER).map(::episod),
            sjukdomsIncheckningar = rows(LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR).map(::incheckning),
            handelser = rows(LegacyRoomSchema.HEALTH_EVENTS).map(::handelse),
            notes = rows(LegacyRoomSchema.NOTES).map { NoteJson(target = it.text(TARGET, LegacyRoomSchema.NOTES), entityId = it.text(ENTITY_ID, LegacyRoomSchema.NOTES), text = it.text(TEXT, LegacyRoomSchema.NOTES)) },
            screeningEventConfigs = screeningEventConfigs(),
            medNotificationConfigs = medNotificationConfigs(),
            sheetsConfig = string(Prefs.SHEETS_CONFIG, null)?.takeIf { it.isNotBlank() },
            handelseTypOptions = eventOptions,
            periodReminderTime = string(Prefs.PERIOD_REMINDER_TIME, LegacyDefaults.PERIOD_REMINDER_TIME)!!,
            settings = SettingsBackup(
                medsNotificationsEnabled = bool(Prefs.MEDS_NOTIFICATIONS, LegacyDefaults.MEDS_NOTIFICATIONS),
                themeMode = themeMode(),
                themeLightStart = int(Prefs.THEME_LIGHT_START, LegacyDefaults.THEME_LIGHT_START),
                themeDarkStart = int(Prefs.THEME_DARK_START, LegacyDefaults.THEME_DARK_START),
                isDarkTheme = bool(Prefs.IS_DARK_THEME, LegacyDefaults.IS_DARK_THEME),
                dynamicColor = bool(Prefs.DYNAMIC_COLOR, LegacyDefaults.DYNAMIC_COLOR),
                birthYear = intOrNull(Prefs.BIRTH_YEAR),
                sex = sex(),
            ),
        )
        return LegacyAssembly(backup, warnings.toList())
    }

    private fun rows(table: String): List<Row> = tables[table].orEmpty()

    // ── Tabellrader → 3.x-klasserna (3.x `*Entity.toDomain()` + `BackupAssembler.toJson()`) ─────────

    private fun aktivitet(r: Row): AktivitetJson {
        val path = r.path(LegacyRoomSchema.AKTIVITETER)
        return AktivitetJson(
            id = r.text(ID, path),
            timestamp = r.text(TIMESTAMP, path),
            datum = r.text(DATUM, path),
            tid = r.text(TID, path),
            aktivitet = r.text("aktivitet", path),
            energy = r.int("energy", path),
            stress = r.int("stress", path),
            somatiska = r.int(SOMATISKA, path),
            symptom = r.text(SYMPTOM, path),
            aterhamtande = r.bool("aterhamtande", path),
            energitjuv = r.bool("energitjuv", path),
            type = r.text("type", path),
            spentTime = r.intOrNull("spentTime", path),
        )
    }

    private fun medicin(r: Row): MedicinJson {
        val path = r.path(LegacyRoomSchema.MEDICINER)
        return MedicinJson(
            id = r.text(ID, path),
            timestamp = r.text(TIMESTAMP, path),
            datum = r.text(DATUM, path),
            tid = r.text(TID, path),
            namn = r.text(NAMN, path),
            dos = r.text(DOS, path),
            enhet = r.text(ENHET, path),
            tidpunkt = r.text(TIDPUNKT, path),
            tagen = r.bool("tagen", path),
            receptId = r.textOrNull("receptId", path),
            skipped = r.bool("skipped", path),
            tagenTid = r.textOrNull("tagenTid", path),
        )
    }

    private fun recept(r: Row): ReceptJson {
        val path = r.path(LegacyRoomSchema.RECEPT)
        return ReceptJson(
            id = r.text(ID, path),
            namn = r.text(NAMN, path),
            dos = r.text(DOS, path),
            enhet = r.text(ENHET, path),
            tidpunkter = decode(path, "tidpunkterJson", r.text("tidpunkterJson", path), emptyList<String>()),
            upprepning = r.text("upprepning", path),
            dagar = decode(path, "dagarJson", r.text("dagarJson", path), emptyList<Int>()),
            intervalDagar = r.int("intervalDagar", path),
            aktiv = r.bool("aktiv", path),
            skapad = r.text("skapad", path),
            startDatum = r.text(START_DATUM, path),
            slutDatum = r.textOrNull(SLUT_DATUM, path),
            dosperioder = decode(path, "dosperioderJson", r.text("dosperioderJson", path), emptyList<StoredDosperiod>())
                .map { DosperiodJson(id = it.id, startDatum = it.startDatum, slutDatum = it.slutDatum, dos = it.dos, enhet = it.enhet) },
        )
    }

    private fun favorit(r: Row): FavoritJson {
        val path = r.path(LegacyRoomSchema.FAVORITER)
        return FavoritJson(
            id = r.text(ID, path),
            namn = r.text(NAMN, path),
            dos = r.text(DOS, path),
            enhet = r.text(ENHET, path),
            tidpunkt = r.text(TIDPUNKT, path),
            minTidMellan = r.int("minTidMellan", path),
            dispenseringsTid = r.text("dispenseringsTid", path),
            maxDoserPerDag = r.int("maxDoserPerDag", path),
            isFavorite = r.bool("isFavorite", path),
        )
    }

    private fun handelse(r: Row): HandelseJson {
        val path = r.path(LegacyRoomSchema.HEALTH_EVENTS)
        return HandelseJson(
            id = r.text(ID, path),
            timestamp = r.text(TIMESTAMP, path),
            datum = r.text(DATUM, path),
            tid = r.text(TID, path),
            typ = r.text(TYP, path),
            svarighetsgrad = r.int(SVARIGHETSGRAD, path),
            varaktighetMinuter = r.int("varaktighetMinuter", path),
            triggers = r.text("triggers", path),
            atgarder = r.text("atgarder", path),
        )
    }

    private fun episod(r: Row): SjukdomsEpisodJson {
        val path = r.path(LegacyRoomSchema.SJUKDOMSEPISODER)
        return SjukdomsEpisodJson(
            id = r.text(ID, path),
            typ = r.text(TYP, path),
            startDatum = r.text("start_datum", path),
            slutDatum = r.text("slut_datum", path),
            timestamp = r.long(TIMESTAMP, path),
        )
    }

    private fun incheckning(r: Row): SjukdomsIncheckningJson {
        val path = r.path(LegacyRoomSchema.SJUKDOMS_INCHECKNINGAR)
        return SjukdomsIncheckningJson(
            id = r.text(ID, path),
            episodId = r.text("episod_id", path),
            datum = r.text(DATUM, path),
            tid = r.text(TID, path),
            svarighetsgrad = r.int(SVARIGHETSGRAD, path),
            symptom = r.text(SYMPTOM, path),
            somatiska = r.int(SOMATISKA, path),
            timestamp = r.long(TIMESTAMP, path),
        )
    }

    // ── Talkolumner: INTEGER är Long; en annan typ är inte vad Room skrev och ger 0 med varning ──────

    /** INTEGER (Room: Long/Int/Boolean); REAL, TEXT och BLOB är en annan typ än 3.x sparade och läses som 0 med varning. */
    private fun Row.long(column: String, path: String): Long = when (val value = this[column]) {
        null -> 0L
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        else -> 0L.also { warn(path, "$column har en annan typ än 3.x sparade – läses som 0") }
    }

    /** TEXT; ett tal läses som sin text (som 3.x:s cursor), en BLOB som tom med varning. */
    private fun Row.text(column: String, path: String): String = when (val value = this[column]) {
        null -> ""
        is String -> value
        is ByteArray -> "".also { warn(path, "$column är binär (BLOB) – läses som tom text") }
        else -> value.toString()
    }

    private fun Row.textOrNull(column: String, path: String): String? = this[column]?.let { text(column, path) }

    private fun Row.int(column: String, path: String): Int = long(column, path).toInt()

    private fun Row.intOrNull(column: String, path: String): Int? = this[column]?.let { int(column, path) }

    /** Room lagrar `Boolean` som INTEGER 0/1. */
    private fun Row.bool(column: String, path: String): Boolean = long(column, path) != 0L

    /** Radens plats i rapporten: tabellen och 3.x-id:t (eller dess längd). */
    private fun Row.path(table: String): String = "$table/${idLabel(text(ID, table))}"

    // ── DataStore → 3.x `PreferencesRepository` (standardvärde när nyckeln saknas eller inte går att läsa) ──

    /** 3.x `decodeOptions`: `List<SymptomOption>`, annars den äldre `List<String>`, annars standardlistan med varning. */
    private fun options(key: String, defaults: List<String>): List<SymptomOptionBackup> {
        val raw = string(key, null) ?: return defaults.map(::SymptomOptionBackup)
        decodeOrNull<List<SymptomOptionBackup>>(raw)?.let { return it }
        decodeOrNull<List<String>>(raw)?.let { list -> return list.map(::SymptomOptionBackup) }
        warn(key, "kunde inte avkoda alternativlistan (${raw.length} tecken) – 3.x standardlista gäller, som i 3.x")
        return defaults.map(::SymptomOptionBackup)
    }

    private fun screeningEventConfigs(): List<ScreeningEventConfigJson> =
        storedList<StoredReminder>(Prefs.SCREENING_EVENT_CONFIGS)?.map { ScreeningEventConfigJson(enabled = it.enabled, time = it.time) }
            ?: LegacyDefaults.SCREENING_EVENT_CONFIGS

    /** Som 3.x `BackupAssembler`: raden får tidpunktens namn ur positionen; en sjunde rad får inget namn. */
    private fun medNotificationConfigs(): List<MedNotificationConfigJson> =
        storedList<StoredReminder>(Prefs.MED_NOTIFICATION_CONFIGS)?.mapIndexed { index, config ->
            MedNotificationConfigJson(tidpunkt = Slot.SCHEDULED.getOrNull(index)?.legacyName.orEmpty(), enabled = config.enabled, time = config.time)
        } ?: LegacyDefaults.MED_NOTIFICATION_CONFIGS

    /** `null` = nyckeln saknas eller värdet går inte att läsa (då med varning) – anroparen tar 3.x:s standardlista. */
    private inline fun <reified T> storedList(key: String): List<T>? {
        val raw = string(key, null) ?: return null
        return decodeOrNull<List<T>>(raw) ?: null.also { warn(key, "kunde inte avkoda listan (${raw.length} tecken) – 3.x standardvärden gäller, som i 3.x") }
    }

    /** Ett blankt temaläge är inget läge: räknas som saknat, med varning. */
    private fun themeMode(): String = string(Prefs.THEME_MODE, null)?.let { mode ->
        mode.takeIf { it.isNotBlank() } ?: null.also { warn(Prefs.THEME_MODE, "tomt värde – räknas som saknat, 3.x standardvärde gäller") }
    } ?: LegacyDefaults.THEME_MODE

    /** 3.x `Sex.fromStorageKey`: okänt eller saknat → ej angivet. */
    private fun sex(): String = string(Prefs.SEX, null)?.takeIf { it in LegacyDefaults.SEX_KEYS } ?: LegacyDefaults.SEX_UNSPECIFIED

    private fun string(key: String, default: String?): String? = prefs[key]?.let { it as? String ?: null.also { wrongType(key) } } ?: default

    private fun int(key: String, default: Int): Int = intOrNull(key) ?: default

    private fun intOrNull(key: String): Int? = prefs[key]?.let { (it as? Number)?.toInt() ?: null.also { wrongType(key) } }

    private fun bool(key: String, default: Boolean): Boolean = prefs[key]?.let { it as? Boolean ?: null.also { wrongType(key) } } ?: default

    private fun wrongType(key: String) = warn(key, "värdet har en annan typ än 3.x sparade – 3.x standardvärde gäller")

    // ── JSON-kolumner och JSON-värden, som 3.x läste dem (`Json { ignoreUnknownKeys = true }`) ──────

    /** 3.x `decodeStringList`/`decodeIntList`/`decodeDosperioder`: ett värde som inte går att läsa ger tom lista med varning. */
    private inline fun <reified T> decode(path: String, column: String, raw: String, fallback: T): T =
        decodeOrNull<T>(raw) ?: fallback.also { warn(path, "$column går inte att läsa (${raw.length} tecken) – tom lista, som i 3.x") }

    private inline fun <reified T> decodeOrNull(raw: String): T? = runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private fun warn(path: String, message: String) {
        warnings += Warning(path, message)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        // Kolumnnamn som delas av flera tabeller (11.json); unika namn står vid sin rad.
        const val ID = "id"
        const val TIMESTAMP = "timestamp"
        const val DATUM = "datum"
        const val TID = "tid"
        const val NAMN = "namn"
        const val DOS = "dos"
        const val ENHET = "enhet"
        const val TIDPUNKT = "tidpunkt"
        const val TYP = "typ"
        const val SYMPTOM = "symptom"
        const val SOMATISKA = "somatiska"
        const val SVARIGHETSGRAD = "svarighetsgrad"
        const val START_DATUM = "startDatum"
        const val SLUT_DATUM = "slutDatum"
        const val TARGET = "target"
        const val ENTITY_ID = "entityId"
        const val TEXT = "text"
    }
}

/** 3.x `Dosperiod` som den låg i `recept.dosperioderJson` (branchen `legacy`, `domain/model/Medicin.kt`). */
@Serializable
private data class StoredDosperiod(
    val id: String,
    val startDatum: String,
    val slutDatum: String? = null,
    val dos: String,
    val enhet: String,
)

/** 3.x `ScreeningEventConfig` och `MedNotificationConfig` (`data/datastore/PreferencesRepository.kt`): båda `{enabled, time}` utan standardvärden. */
@Serializable
private data class StoredReminder(val enabled: Boolean, val time: String)

// SQLite lämnar TEXT som String, INTEGER som Long och NULL som null; en kolumn som saknas läses som NULL.

