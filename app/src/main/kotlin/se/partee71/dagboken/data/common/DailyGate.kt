package se.partee71.dagboken.data.common

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/**
 * Enhetslokal anteckning (TP-4): per nyckel den senast antecknade dagen – dagsgrinden ([DailyGate]) och historiklägets
 * täckning ([HistoryCoverage], NFR-24). Bara ett datum per nyckel – ingen hälsodata, aldrig i Firestore eller exporten.
 */
interface DayMarks {
    /** Den antecknade dagen, `null` om ingen (eller om den inte går att läsa). */
    suspend fun get(key: String): LocalDate?

    suspend fun set(key: String, day: LocalDate)

    suspend fun remove(key: String)

    /** Tar bort varje nyckel som börjar med [prefix]. */
    suspend fun removeStartingWith(prefix: String)
}

/** [DayMarks] i 4.0:s egen DataStore-fil (`dagboken_device`), som överlever processdöd. */
class DataStoreDayMarks @Inject constructor(private val store: DataStore<Preferences>) : DayMarks {
    override suspend fun get(key: String): LocalDate? =
        store.data.first()[preference(key)]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun set(key: String, day: LocalDate) {
        store.edit { it[preference(key)] = day.toString() }
    }

    override suspend fun remove(key: String) {
        store.edit { it.remove(preference(key)) }
    }

    override suspend fun removeStartingWith(prefix: String) {
        store.edit { prefs -> prefs.asMap().keys.filter { it.name.startsWith(PREFIX + prefix) }.forEach { prefs.remove(it) } }
    }

    private fun preference(key: String) = stringPreferencesKey(PREFIX + key)

    private companion object {
        const val PREFIX = "daily_gate_"
    }
}

/**
 * Dagsgrinden (#304): en åtgärd körs högst en gång per dag, enhet och konto ([UserScope.uid]) **efter ett lyckat
 * utfall**. Bara `Result.success` antecknas (i [DayMarks], överlever processdöd); ett fel – också
 * [DataError.Offline] – antecknas inte, så nästa anrop samma dag försöker igen. Samtidiga anrop med samma namn, dag
 * och konto ger en körning: de som väntat på den får dess utfall (single-flight). Utan inloggning körs åtgärden utan
 * grind (den misslyckas själv). Går anteckningen inte att läsa eller skriva körs åtgärden hellre en gång för mycket.
 * En instans per åtgärd (ingen `@Singleton`: den som injiceras får en egen).
 */
class DailyGate @Inject constructor(private val marks: DayMarks, private val user: UserScope) {
    private val mutex = Mutex()

    /** Antal avslutade körningar – för att känna igen en körning som pågick när ett anrop kom. */
    private val finished = AtomicLong()

    /** Senaste avslutade körningen; läses och skrivs bara under [mutex]. */
    private var last: Attempt? = null

    suspend fun runOncePerDay(name: String, day: LocalDate, action: suspend () -> Result<Unit>): Result<Unit> {
        val uid = user.uid.value ?: return action()
        val key = "$name.$uid"
        val seen = finished.get()
        return mutex.withLock {
            // En körning för samma konto och dag avslutades medan det här anropet väntade: dess utfall gäller.
            last?.takeIf { it.key == key && it.day == day && it.number > seen }?.let { return@withLock it.result }
            if (read(key) == day) return@withLock Result.success(Unit)
            val result = action()
            // Ett kontobyte under körningen: utfallet gäller inte säkert det nya kontot, så ingenting antecknas.
            if (result.isSuccess && user.uid.value == uid) write(key, day)
            last = Attempt(key, day, finished.incrementAndGet(), result)
            result
        }
    }

    /**
     * Glömmer kontots antecknade dag för [name], så att nästa anrop kör igen – t.ex. efter en egen skrivning vars följder
     * åtgärden läker (en receptsparning vars dossynk avbröts, REC-10). Väntar in en pågående körning.
     */
    suspend fun forget(name: String) {
        val uid = user.uid.value ?: return
        val key = "$name.$uid"
        mutex.withLock {
            if (last?.key == key) last = null
            suspendRunCatching({ DataError.Unknown }) { marks.remove(key) }
        }
    }

    private suspend fun read(key: String): LocalDate? = suspendRunCatching({ DataError.Unknown }) { marks.get(key) }.getOrNull()

    private suspend fun write(key: String, day: LocalDate) {
        suspendRunCatching({ DataError.Unknown }) { marks.set(key, day) }
    }

    private class Attempt(val key: String, val day: LocalDate, val number: Long, val result: Result<Unit>)
}
