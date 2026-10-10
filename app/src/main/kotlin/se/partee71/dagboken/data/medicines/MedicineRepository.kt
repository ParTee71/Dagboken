package se.partee71.dagboken.data.medicines

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import se.partee71.dagboken.core.medicine.MedicineCatalog

/** Läkemedelslistan som är inbyggd i appen (REC-14) – läses en gång, sedan ur minnet. */
interface MedicineRepository {
    suspend fun catalog(): MedicineCatalog

    /** Listans datum – för Om Dagboken, som inte ska behöva tolka hela listan. */
    suspend fun updated(): LocalDate? = catalog().updated
}

/**
 * Läser `assets/medicines.tsv` (byggd av `tools/medicines`). Saknas eller går filen inte att läsa blir
 * listan tom: formuläret fungerar då som utan förslag. Helt lokalt – inget om sökningar loggas eller skickas (NFR-8).
 */
@Singleton
class AssetMedicineRepository internal constructor(private val open: () -> InputStream) : MedicineRepository {
    @Inject constructor(@ApplicationContext context: Context) : this({ context.assets.open(FILE) })

    private val lock = Mutex()
    private var loaded: MedicineCatalog? = null

    /** Ett misslyckande ger en tom lista men cachas inte: nästa anrop försöker läsa filen igen. */
    override suspend fun catalog(): MedicineCatalog = lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            runCatching { open().bufferedReader().use { MedicineCatalog.parse(it.readText()) } }.getOrNull()
        }?.also { loaded = it } ?: MedicineCatalog(null, emptyList())
    }

    private var date: Result<LocalDate?>? = null

    /**
     * Datumet ur filens första rader – läser inte resten av listan. Ett läst datum cachas (även `null` när
     * raden saknas); ett läsfel gör det inte.
     */
    override suspend fun updated(): LocalDate? = lock.withLock {
        loaded?.let { return@withLock it.updated }
        date?.let { return@withLock it.getOrNull() }
        withContext(Dispatchers.IO) {
            runCatching { open().bufferedReader().useLines { lines -> MedicineCatalog.parseUpdated(lines.take(HEADER_LINES)) } }
        }.also { if (it.isSuccess) date = it }.getOrNull()
    }

    private companion object {
        const val FILE = "medicines.tsv"
        const val HEADER_LINES = 5
    }
}
