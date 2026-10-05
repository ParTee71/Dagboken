package se.partee71.dagboken.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.partee71.dagboken.core.engine.hasActiveName
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.schema.OptionCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.sortOrderAfterLast

/**
 * Alternativlistorna – aktivitetstyper, symptom och händelsetyper i samlingen `options` (DAT-9).
 * Poster refererar ett alternativ med dess id, så ett namnbyte syns i historiken (SET-11); ett
 * alternativ arkiveras, det raderas aldrig.
 */
interface OptionsRepository {
    /** Listans alternativ, arkiverade med, i listans ordning (`sortOrder`). */
    fun observe(kind: OptionKind): Flow<List<Option>>

    suspend fun get(id: String): Result<Option?>

    /**
     * Lägger till [name] sist i listan [kind] med id `OptionIds.of(kind, name)` (DAT-13) – samma id
     * som konverteraren ger 3.x-alternativet med samma namn. Finns ett arkiverat alternativ med just
     * det namnet återställs det i stället (samma id, favorit och plats). Har id:t redan ett annat
     * namn (alternativet har bytt namn sedan dess) får det nya alternativet ett slumpat id – ett
     * befintligt dokument skrivs aldrig över. Står namnet redan bland listans aktiva alternativ
     * misslyckas det med [DuplicateOptionName]; går listan inte att läsa ur cachen misslyckas det
     * med `DataError` och ingenting skrivs.
     *
     * Accepterad risk: kontrollen görs mot cachen. Är den ofullständig (första synken inte klar)
     * och har servern ett omdöpt alternativ på just det id:t, får det tillbaka namnet [name] och en
     * ny plats och blir aktivt – stjärna och okända fält står kvar (bara `name`, `kind`,
     * `sortOrder` och `archived = false` skrivs). Det går inte att utesluta offline, eftersom
     * Firestore saknar en skrivning som bara skapar.
     */
    suspend fun add(kind: OptionKind, name: String): Result<Unit>

    /**
     * Byter namn; id:t är kvar, så redan loggade poster visar det nya namnet (SET-11). Skriver bara
     * fältet `name` – favorit, plats och arkivering från en annan skärm eller enhet står kvar.
     * Ett namn som ett annat aktivt alternativ i listan har ger [DuplicateOptionName].
     */
    suspend fun rename(option: Option, name: String): Result<Unit>

    /** Stjärnmärker (SET-5, SET-9); skriver bara fältet `favorite`. */
    suspend fun setFavorite(option: Option, favorite: Boolean): Result<Unit>

    /**
     * Arkiverar eller återställer (DAT-9); skriver bara fältet `archived`. Att återställa när ett
     * aktivt alternativ i samma lista redan har namnet (oavsett skiftläge) ger [DuplicateOptionName]
     * och ingenting skrivs (SET-5, SET-6, SET-9); att arkivera nekas aldrig.
     */
    suspend fun setArchived(id: String, archived: Boolean): Result<Unit>
}

/**
 * Namnet står redan bland listans aktiva alternativ (samma namn oavsett skiftläge, SET-5, SET-6,
 * SET-9) – ingenting skrevs. Ett domänfel, inget `DataError`: UI visar "finns redan".
 */
class DuplicateOptionName(val name: String) : Exception()

/** Tunn fasad över samlingen `options` (skill firestore-data-layer). */
class DefaultOptionsRepository @Inject constructor(collections: CollectionFactory) : OptionsRepository {
    private val collection = collections.options()

    override fun observe(kind: OptionKind): Flow<List<Option>> = collection.observe().map { all -> all.filter { it.kind == kind } }

    override suspend fun get(id: String): Result<Option?> = collection.get(id)

    override suspend fun add(kind: OptionKind, name: String): Result<Unit> {
        val trimmed = name.trim()
        val all = collection.cached().getOrElse { return Result.failure(it) }
        all.duplicateOf(kind, trimmed, exceptId = null)?.let { return Result.failure(it) }
        val deterministic = OptionIds.of(kind, trimmed)
        val existing = all.firstOrNull { it.id == deterministic }
        if (existing != null && existing.name == trimmed) return restore(existing)
        // Id:t upptaget av ett alternativ som bytt namn: ett eget id, så att det aldrig skrivs över (SET-11).
        val id = if (existing == null) deterministic else collection.newId()
        // Namn, sort, plats och aktiv (användaren lägger uttryckligen till namnet): finns dokumentet
        // ändå (cachen var ofullständig) nollställs aldrig dess stjärna; ett nytt får standardvärdet.
        return collection.merge(Option(id, kind, trimmed, sortOrder = all.sortOrderAfterLast()), NEW_OPTION_FIELDS)
    }

    override suspend fun rename(option: Option, name: String): Result<Unit> {
        val trimmed = name.trim()
        collection.cached().getOrElse { return Result.failure(it) }.duplicateOf(option.kind, trimmed, exceptId = option.id)?.let { return Result.failure(it) }
        return collection.update(option.copy(name = trimmed), setOf(OptionCodec.NAME))
    }

    override suspend fun setFavorite(option: Option, favorite: Boolean): Result<Unit> =
        collection.update(option.copy(favorite = favorite), setOf(OptionCodec.FAVORITE))

    override suspend fun setArchived(id: String, archived: Boolean): Result<Unit> {
        if (archived) return collection.setArchived(id, true)
        val all = collection.cached().getOrElse { return Result.failure(it) }
        // Finns det inte i cachen finns inget namn att pröva; `setArchived` skapar inget dokument.
        val stored = all.firstOrNull { it.id == id } ?: return collection.setArchived(id, false)
        all.duplicateOf(stored.kind, stored.name, exceptId = id)?.let { return Result.failure(it) }
        return restore(stored)
    }

    /** Återställer bara fältet `archived` – favorit, plats och namn står kvar som de är lagrade. */
    private suspend fun restore(option: Option) = collection.update(option.copy(archived = false), setOf(OptionCodec.ARCHIVED))

    /** [DuplicateOptionName] om [name] redan står bland de aktiva i listan [kind] (annat än [exceptId]), annars `null`. */
    private fun List<Option>.duplicateOf(kind: OptionKind, name: String, exceptId: String?): DuplicateOptionName? =
        if (filter { it.kind == kind }.hasActiveName(name, exceptId)) DuplicateOptionName(name) else null

    private companion object {
        val NEW_OPTION_FIELDS = setOf(listOf(OptionCodec.NAME), listOf(OptionCodec.KIND), listOf(OptionCodec.SORT_ORDER), listOf(OptionCodec.ARCHIVED))
    }
}
