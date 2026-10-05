package se.partee71.dagboken.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.schema.SettingsCodec
import se.partee71.dagboken.data.common.CollectionFactory
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.FieldPath
import se.partee71.dagboken.data.common.KeyedList
import se.partee71.dagboken.data.common.changedFields
import se.partee71.dagboken.data.common.withChangedRows

/**
 * Användarens inställningar – dokumentet `settings/app` (DAT-11). Ett saknat dokument betyder
 * standardvärdena ([Settings]), som för en ny användare.
 */
interface SettingsRepository {
    /** Inställningarna ur cachen vid varje ändring; följer den inloggade användaren. */
    val settings: Flow<Settings>

    /**
     * Det lagrade värdet, en gång (formulären) – ur cachen först, offline först. Standardvärdena
     * bara när dokumentet bekräftat saknas (ny användare); finns det inte i cachen och servern inte
     * nås blir det `DataError.Offline` (formuläret visar laddfel med Försök igen) – standardvärden
     * visas aldrig som om de vore lagrade.
     */
    suspend fun get(): Result<Settings>

    /**
     * Sparar ett formulär: bara fälten som skiljer [edited] från [loaded] – värdet formuläret
     * laddades med – skrivs (`EntityCollection.merge`, t.ex. `profile.birthYear`). Ett fält som en
     * annan enhet ändrat efter att formuläret laddades, och som användaren inte rört, skrivs alltså
     * inte tillbaka; okända fält och `legacy` står kvar (DAT-11). Är inget ändrat skrivs ingenting.
     * Påminnelseraderna skrivs rad för rad: bara ändrade rader läggs på den lagrade listan. Finns
     * inget lagrat att bygga på (inte i cachen och servern nås inte) skrivs listorna inte, övriga
     * ändringar skrivs, och resultatet blir `DataError.Offline`; vid andra fel skrivs ingenting.
     */
    suspend fun save(loaded: Settings, edited: Settings): Result<Unit>

    /**
     * Ändrar inställningarna direkt (t.ex. ett val på temaskärmen): [change] av det lagrade ([get])
     * avgör vad som ändrats, och bara de fälten skrivs, som i [save]. Ändringar körs en i taget, så
     * två snabba ändringar bygger på varandra. Är dokumentet bekräftat saknat skapas det med bara
     * ändringen. Utan bekräftat underlag (inte i cachen och servern nås inte: `DataError.Offline`),
     * utloggad eller nekad läsning: ingenting skrivs.
     */
    suspend fun update(change: (Settings) -> Settings): Result<Unit>
}

/** Tunn fasad över samlingen `settings` (skill firestore-data-layer). */
@Singleton
class DefaultSettingsRepository @Inject constructor(collections: CollectionFactory) : SettingsRepository {
    private val collection = collections.settings()

    override val settings: Flow<Settings> = collection.observe(Settings.ID).map { it ?: Settings() }

    /** Standardvärden bara när dokumentet bekräftat saknas; inte i cachen och servern nås inte → `Offline`. */
    override suspend fun get(): Result<Settings> = collection.cached(Settings.ID).map { it ?: Settings() }

    /**
     * En skrivning i taget i hela appen (repositoryt är en singleton). Underlaget läses **inom**
     * låset, för korrektheten: lästes det före, kunde en samtidig skrivning hinna emellan – en
     * ändring som jämför mot ett gammalt värde (mörkt och sedan tillbaka till auto i snabb följd ser
     * ut som "ingen ändring"), eller påminnelserader som byggs på en lista som just ändrats, tappas.
     * Läsningen tar millisekunder när cachen kan svara; annars väntar den högst tills Firestore vet
     * att den är offline.
     */
    private val writes = Mutex()

    override suspend fun save(loaded: Settings, edited: Settings): Result<Unit> = writes.withLock { write(loaded, edited) }

    override suspend fun update(change: (Settings) -> Settings): Result<Unit> = writes.withLock {
        get().fold(onSuccess = { stored -> write(stored, change(stored)) }, onFailure = { Result.failure(it) })
    }

    private suspend fun write(before: Settings, after: Settings): Result<Unit> {
        val beforeDoc = SettingsCodec.encode(before)
        val afterDoc = SettingsCodec.encode(after)
        val fields = changedFields(beforeDoc, afterDoc)
        val lists = REMINDER_ROWS.filterTo(mutableSetOf()) { it.path in fields }
        if (lists.isEmpty()) return mergeIfAny(after, fields)
        val listPaths = lists.mapTo(mutableSetOf()) { it.path }
        val stored = collection.cached(Settings.ID).getOrElse { error ->
            // Bara offline (inte i cachen, servern nås inte) skrivs övriga ändringar ändå – raderna
            // aldrig, så att standardrader inte ersätter serverns. Annat fel: ingenting skrivs.
            if (error != DataError.Offline) return Result.failure(error)
            return Result.failure(mergeIfAny(after, fields - listPaths).exceptionOrNull() ?: error)
        } ?: Settings() // bekräftat saknas: standardvärdena är det lagrade
        val storedDoc = SettingsCodec.encode(stored)
        val rebased = withChangedRows(beforeDoc, afterDoc, storedDoc, lists)
        val rows = changedFields(storedDoc, rebased).filterTo(mutableSetOf()) { it in listPaths }
        return mergeIfAny(SettingsCodec.decode(Settings.ID, rebased), fields - listPaths + rows)
    }

    private suspend fun mergeIfAny(settings: Settings, fields: Set<FieldPath>): Result<Unit> =
        if (fields.isEmpty()) Result.success(Unit) else collection.merge(settings, fields)

    private companion object {
        /**
         * Påminnelseraderna, nycklade på `slot` och `occasion`. Fasta uppsättningar: `SettingsCodec`
         * avkodar alltid exakt en rad per nyckel i `Slot.SCHEDULED` respektive `Occasion.entries`,
         * i den ordningen (en saknad rad får standardvärdet, en okänd nyckel hoppas över), och kodar
         * dem så – rader läggs aldrig till eller tas bort.
         */
        val REMINDER_ROWS = setOf(
            KeyedList(listOf(SettingsCodec.REMINDERS, SettingsCodec.MED_SLOTS), key = "slot"),
            KeyedList(listOf(SettingsCodec.REMINDERS, SettingsCodec.SCREENING_OCCASIONS), key = "occasion"),
        )
    }
}
