package se.partee71.dagboken.data.medicines

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import se.partee71.dagboken.core.medicine.MedicineCatalog

/** Läkemedelslistan som är inbyggd i appen (REC-14) – läses en gång, sedan ur minnet. */
interface MedicineRepository {
    suspend fun catalog(): MedicineCatalog
}

/**
 * Läser `assets/medicines.tsv` (byggd av `tools/medicines`). Saknas eller går filen inte att läsa blir
 * listan tom: formuläret fungerar då som utan förslag. Helt lokalt – inget om sökningar loggas eller skickas (NFR-8).
 */
@Singleton
class AssetMedicineRepository @Inject constructor(@param:ApplicationContext private val context: Context) : MedicineRepository {
    private val lock = Mutex()
    private var loaded: MedicineCatalog? = null

    override suspend fun catalog(): MedicineCatalog = lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            runCatching { context.assets.open(FILE).bufferedReader().use { MedicineCatalog.parse(it.readText()) } }
                .getOrElse { MedicineCatalog(null, emptyList()) }
        }.also { loaded = it }
    }

    private companion object {
        const val FILE = "medicines.tsv"
    }
}
