package se.partee71.dagboken.core.legacy

/**
 * Namnen i 3.x:s lagring på enheten (branchen `legacy`): Room-databasen `dagboken.db` i schemaversion 11
 * (`app/schemas/…/AppDatabase/11.json`, kopierad till `tools/db/test/fixtures/legacy/room-schema-11.json`)
 * och DataStore-filen `dagboken_prefs` (`data/datastore/PreferencesRepository.kt`). Legacy-läsaren i
 * `:app` (`data/legacy`) läser tabellerna och nycklarna härifrån och lämnar raderna till
 * [LegacyRoomAssembler] – ingen egen mappning i `:app` (OMB-2).
 */
object LegacyRoomSchema {
    /** Room-filens namn i appens privata databaskatalog (3.x `DatabaseModule`). */
    const val DATABASE_NAME = "dagboken.db"

    /** Den enda schemaversion läsaren godtar (3.27.0); en annan `PRAGMA user_version` är en äldre eller okänd 3.x. */
    const val DATABASE_VERSION = 11

    /** DataStore-filens namn (3.x `preferencesDataStore(name = …)`). */
    const val PREFERENCES_NAME = "dagboken_prefs"

    /** 3.x:s periodiska Drive-backup i WorkManager (`DagbokenApp.scheduleDailyBackup`) – avbokas vid första starten av 4.0. */
    const val BACKUP_WORK_NAME = "dagboken_daily_backup"

    const val AKTIVITETER = "aktiviteter"
    const val MEDICINER = "mediciner"
    const val RECEPT = "recept"
    const val FAVORITER = "favoriter"
    const val HEALTH_EVENTS = "health_events"
    const val NOTES = "notes"
    const val SJUKDOMSEPISODER = "sjukdomsepisoder"
    const val SJUKDOMS_INCHECKNINGAR = "sjukdoms_incheckningar"

    /** Alla tabeller i v11, i schemats ordning – läsaren läser varje rad i var och en. */
    val TABLES: List<String> = listOf(AKTIVITETER, MEDICINER, RECEPT, FAVORITER, HEALTH_EVENTS, NOTES, SJUKDOMSEPISODER, SJUKDOMS_INCHECKNINGAR)

    /** 3.x `PreferencesRepository.Keys` – de nycklar som är användardata (`migration_done` och `backup_needs_auth` är det inte). */
    object Prefs {
        const val AKTIVITET_OPTIONS = "aktivitet_options"
        const val SYMPTOM_OPTIONS = "symptom_options"
        const val HANDELSE_TYP_OPTIONS = "handelse_typ_options"
        const val SHEETS_CONFIG = "sheets_config"
        const val THEME_MODE = "theme_mode"
        const val THEME_LIGHT_START = "theme_light_start"
        const val THEME_DARK_START = "theme_dark_start"
        const val IS_DARK_THEME = "is_dark_theme"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val MEDS_NOTIFICATIONS = "meds_notifications"
        const val MED_NOTIFICATION_CONFIGS = "med_notification_configs"
        const val SCREENING_EVENT_CONFIGS = "screening_event_configs"
        const val PERIOD_REMINDER_TIME = "period_reminder_time"
        const val BIRTH_YEAR = "birth_year"
        const val SEX = "sex"
    }
}
