package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.schema.PrnMedicineCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.updateChanged

/** Vid behov-medicinerna i samlingen `prnMedicines` (FAV-1…FAV-11, MEDF-3, SET-10). */
interface PrnMedicineRepository {
    fun observe(): Flow<List<PrnMedicine>>

    suspend fun get(id: String): Result<PrnMedicine?>

    /** En ny vid behov-medicin med ett nytt, slumpat id ([medicine]s id används inte). */
    suspend fun add(medicine: PrnMedicine): Result<Unit>

    /**
     * Sparar formuläret: bara fälten som skiljer [edited] från [loaded] skrivs – stjärnan,
     * dispenseringstiden (FAV-7) och fält från en nyare app står kvar som de är lagrade, och en
     * medicin som raderats under tiden återuppstår inte.
     */
    suspend fun save(loaded: PrnMedicine, edited: PrnMedicine): Result<Unit>

    /** Stjärnmärker (FAV-2, SET-10); skriver bara fältet `favorite`. */
    suspend fun setFavorite(medicine: PrnMedicine, favorite: Boolean): Result<Unit>

    /** Tar bort medicinen; loggade doser står kvar. */
    suspend fun delete(id: String): Result<Unit>
}

/** Tunn fasad över samlingen `prnMedicines` (skill firestore-data-layer). */
class DefaultPrnMedicineRepository @Inject constructor(collections: CollectionFactory) : PrnMedicineRepository {
    private val collection = collections.prnMedicines()

    override fun observe(): Flow<List<PrnMedicine>> = collection.observe()

    override suspend fun get(id: String): Result<PrnMedicine?> = collection.get(id)

    override suspend fun add(medicine: PrnMedicine): Result<Unit> = collection.upsert(medicine.copy(id = collection.newId()))

    override suspend fun save(loaded: PrnMedicine, edited: PrnMedicine): Result<Unit> =
        collection.updateChanged(PrnMedicineCodec, loaded, edited.copy(id = loaded.id))

    override suspend fun setFavorite(medicine: PrnMedicine, favorite: Boolean): Result<Unit> =
        collection.update(medicine.copy(favorite = favorite), setOf(PrnMedicineCodec.FAVORITE))

    override suspend fun delete(id: String): Result<Unit> = collection.delete(id)
}
