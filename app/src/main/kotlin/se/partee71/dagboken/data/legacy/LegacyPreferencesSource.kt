package se.partee71.dagboken.data.legacy

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.legacy.LegacyRoomSchema
import se.partee71.dagboken.di.IoDispatcher

/** 3.x:s DataStore-fil `dagboken_prefs` på enheten, bara läsning (OMB-2): nyckelnamn → råa värden. */
interface LegacyPreferencesSource {
    /** Alla nycklar med sina värden; tom när filen saknas (då gäller 3.x:s standardvärden). Ett läsfel kastas. */
    suspend fun read(): Map<String, Any?>
}

/**
 * Öppnar den befintliga filen med `PreferenceDataStoreFactory` i ett eget scope, läser en gång och stänger
 * (DataStore tillåter en instans per fil och process). Ingen `ReplaceFileCorruptionHandler`: en trasig fil
 * ger ett fel som visas – den ersätts aldrig med en tom (regel 1). Filen skrivs aldrig.
 */
class LegacyPreferencesReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : LegacyPreferencesSource {
    override suspend fun read(): Map<String, Any?> = withContext(dispatcher) {
        val file = context.preferencesDataStoreFile(LegacyRoomSchema.PREFERENCES_NAME)
        if (!file.exists()) return@withContext emptyMap()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.data.first().asMap().mapKeys { it.key.name }
        } finally {
            // Släpper filen så att den kan öppnas igen (och så att testet kan läsa om den).
            scope.coroutineContext.job.cancelAndJoin()
        }
    }
}
