package se.partee71.dagboken.data.legacy

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Påminnelserna schemaläggs inte **medan [LegacyMigrationUseCase.write] eller [LegacyImportUseCase.write] körs** (OMB-2, BCK-14) – sätts i början och släpps alltid
 * (try/finally) när skrivningen returnerar eller kastar. `ReminderSync` hoppar bara över schemaläggningen under tiden
 * och avbokar aldrig något på grund av pausen; efteråt läggs larmen som vanligt. Ligger i minnet: startvärdet är `false`,
 * så processdöd kräver inget extra.
 */
@Singleton
class LegacyMigrationPause @Inject constructor() {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun set(paused: Boolean) {
        _paused.value = paused
    }

    /** Kör [block] med påminnelserna pausade och släpper alltid pausen efteråt (try/finally) – flytten, avbrytandet och importen. */
    suspend fun <T> during(block: suspend () -> T): T {
        set(true)
        try {
            return block()
        } finally {
            set(false)
        }
    }
}
