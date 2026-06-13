package se.partee71.dagboken.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    val symptomOptions: Flow<List<String>> = dataStore.data
        .map { prefs ->
            prefs[Keys.SYMPTOM_OPTIONS]
                ?.let { Json.decodeFromString<List<String>>(it) }
                ?: DEFAULT_SYMPTOM_OPTIONS
        }

    val sheetsConfig: Flow<String> = dataStore.data
        .map { it[Keys.SHEETS_CONFIG] ?: "" }

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

    suspend fun setSymptomOptions(options: List<String>) {
        dataStore.edit { it[Keys.SYMPTOM_OPTIONS] = Json.encodeToString(options) }
    }

    suspend fun setSheetsConfig(url: String) {
        dataStore.edit { it[Keys.SHEETS_CONFIG] = url }
    }
}

// Mirrors DEFAULT_AKTIVITET_OPTIONS in src/storage/keys.ts
private val DEFAULT_AKTIVITET_OPTIONS = listOf(
    "Promenad", "Jobb", "Möte", "Träning", "Vila", "Mat", "Sällskap", "Läsning", "Övrigt",
)

private val DEFAULT_SYMPTOM_OPTIONS = listOf(
    "Huvudvärk", "Trötthet", "Yrsel", "Smärta", "Illamående", "Övrigt",
)
