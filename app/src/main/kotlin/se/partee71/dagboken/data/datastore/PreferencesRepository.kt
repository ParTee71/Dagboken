package se.partee71.dagboken.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dagboken_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    private object Keys {
        val MIGRATION_DONE      = booleanPreferencesKey("migration_done")
        val IS_DARK_THEME       = booleanPreferencesKey("is_dark_theme")
        val DYNAMIC_COLOR       = booleanPreferencesKey("dynamic_color")
        val AKTIVITET_OPTIONS   = stringPreferencesKey("aktivitet_options")
        val SYMPTOM_OPTIONS     = stringPreferencesKey("symptom_options")
        val SHEETS_CONFIG       = stringPreferencesKey("sheets_config")
        // Auto-theme
        val THEME_MODE          = stringPreferencesKey("theme_mode")      // "light"|"dark"|"auto"
        val THEME_LIGHT_START   = intPreferencesKey("theme_light_start")  // hour 0..23
        val THEME_DARK_START    = intPreferencesKey("theme_dark_start")   // hour 0..23
        // Notifications
        val MEDS_NOTIFICATIONS       = booleanPreferencesKey("meds_notifications")
        val SCREENING_EVENT_CONFIGS  = stringPreferencesKey("screening_event_configs")
        // Backup
        val BACKUP_NEEDS_AUTH   = booleanPreferencesKey("backup_needs_auth")
    }

    val migrationDone: Flow<Boolean> = dataStore.data
        .map { it[Keys.MIGRATION_DONE] ?: false }

    val isDarkTheme: Flow<Boolean> = dataStore.data
        .map { it[Keys.IS_DARK_THEME] ?: true }

    val dynamicColor: Flow<Boolean> = dataStore.data
        .map { it[Keys.DYNAMIC_COLOR] ?: true }

    val aktivitetOptions: Flow<List<String>> = dataStore.data
        .map { prefs ->
            prefs[Keys.AKTIVITET_OPTIONS]
                ?.let { Json.decodeFromString<List<String>>(it) }
                ?: DEFAULT_AKTIVITET_OPTIONS
        }
        .catch { emit(DEFAULT_AKTIVITET_OPTIONS) }

    val symptomOptions: Flow<List<SymptomOption>> = dataStore.data
        .map { prefs ->
            prefs[Keys.SYMPTOM_OPTIONS]
                ?.let { json ->
                    // Try new format first; fall back to migrating old List<String>
                    runCatching { Json.decodeFromString<List<SymptomOption>>(json) }.getOrNull()
                        ?: Json.decodeFromString<List<String>>(json).map { SymptomOption(it) }
                }
                ?: DEFAULT_SYMPTOM_OPTIONS
        }
        .catch { emit(DEFAULT_SYMPTOM_OPTIONS) }

    val sheetsConfig: Flow<String> = dataStore.data
        .map { it[Keys.SHEETS_CONFIG] ?: "" }

    val themeMode: Flow<String> = dataStore.data
        .map { it[Keys.THEME_MODE] ?: "auto" }

    val themeLightStart: Flow<Int> = dataStore.data
        .map { it[Keys.THEME_LIGHT_START] ?: 7 }

    val themeDarkStart: Flow<Int> = dataStore.data
        .map { it[Keys.THEME_DARK_START] ?: 21 }

    val medsNotificationsEnabled: Flow<Boolean> = dataStore.data
        .map { it[Keys.MEDS_NOTIFICATIONS] ?: false }

    val screeningEventConfigs: Flow<List<ScreeningEventConfig>> = dataStore.data
        .map { prefs ->
            prefs[Keys.SCREENING_EVENT_CONFIGS]
                ?.let { Json.decodeFromString<List<ScreeningEventConfig>>(it) }
                ?: DEFAULT_SCREENING_EVENTS
        }
        .catch { emit(DEFAULT_SCREENING_EVENTS) }

    val backupNeedsAuth: Flow<Boolean> = dataStore.data
        .map { it[Keys.BACKUP_NEEDS_AUTH] ?: false }

    suspend fun setMigrationDone(done: Boolean) {
        dataStore.edit { it[Keys.MIGRATION_DONE] = done }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        dataStore.edit { it[Keys.IS_DARK_THEME] = dark }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAktivitetOptions(options: List<String>) {
        dataStore.edit { it[Keys.AKTIVITET_OPTIONS] = Json.encodeToString(options) }
    }

    suspend fun setSymptomOptions(options: List<SymptomOption>) {
        dataStore.edit { it[Keys.SYMPTOM_OPTIONS] = Json.encodeToString(options) }
    }

    suspend fun setSheetsConfig(url: String) {
        dataStore.edit { it[Keys.SHEETS_CONFIG] = url }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setThemeLightStart(hour: Int) {
        dataStore.edit { it[Keys.THEME_LIGHT_START] = hour }
    }

    suspend fun setThemeDarkStart(hour: Int) {
        dataStore.edit { it[Keys.THEME_DARK_START] = hour }
    }

    suspend fun setMedsNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MEDS_NOTIFICATIONS] = enabled }
    }

    suspend fun setScreeningEventConfigs(configs: List<ScreeningEventConfig>) {
        dataStore.edit { it[Keys.SCREENING_EVENT_CONFIGS] = Json.encodeToString(configs) }
    }

    suspend fun setBackupNeedsAuth(needsAuth: Boolean) {
        dataStore.edit { it[Keys.BACKUP_NEEDS_AUTH] = needsAuth }
    }
}

@Serializable
data class SymptomOption(val name: String, val isFavorite: Boolean = false)

@Serializable
data class ScreeningEventConfig(val enabled: Boolean, val time: String)

data class ScreeningTime(val hour: Int, val min: Int) {
    companion object {
        fun parse(s: String): ScreeningTime? {
            val h = s.substringBefore(":").toIntOrNull() ?: return null
            val m = s.substringAfter(":").toIntOrNull() ?: return null
            return ScreeningTime(h, m)
        }
    }
}

val SCREENING_EVENT_LABELS = listOf("Efter frukost", "Lunch", "Kvällsmat", "Läggdags")

val DEFAULT_SCREENING_EVENTS = listOf(
    ScreeningEventConfig(enabled = false, time = "08:00"),
    ScreeningEventConfig(enabled = false, time = "12:00"),
    ScreeningEventConfig(enabled = false, time = "17:00"),
    ScreeningEventConfig(enabled = false, time = "21:00"),
)

private val DEFAULT_AKTIVITET_OPTIONS = listOf(
    "Promenad", "Jobb", "Möte", "Träning", "Vila", "Mat", "Sällskap", "Läsning", "Övrigt",
)

internal val DEFAULT_SYMPTOM_OPTIONS = listOf(
    SymptomOption("Huvudvärk"),
    SymptomOption("Trötthet"),
    SymptomOption("Yrsel"),
    SymptomOption("Smärta"),
    SymptomOption("Illamående"),
    SymptomOption("Övrigt"),
)
