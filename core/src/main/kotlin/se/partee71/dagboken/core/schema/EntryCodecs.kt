package se.partee71.dagboken.core.schema

import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.HasFeelings
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.SymptomScore

// Codecs för alternativen och dagbokens poster (ARKITEKTUR.md → Datamodell). Fältnamnen är
// stabila och engelska; datum `yyyy-MM-dd`, klockslag `HH:mm`, ögonblick som tidsstämpel (DAT-2).

/** Fälten som varje post har – skrivs här, en gång. */
internal const val DATE = "date"
internal const val TIME = "time"
internal const val CREATED_AT = "createdAt"
internal const val NOTE = "note"
internal const val SYMPTOMS = "symptoms"

/** Postens gemensamma fält: dag, klockslag, när den skapades och anteckningen (DAT-7). */
internal fun entryFields(
    date: kotlinx.datetime.LocalDate?,
    time: kotlinx.datetime.LocalTime?,
    createdAt: kotlin.time.Instant?,
    note: String?
): Doc = mapOf(DATE to date.encodeDate(), TIME to time.encodeTime(), CREATED_AT to createdAt, NOTE to note)

/** 3.x `somatiska` som avviker från summan (DAT-6) – på måendeloggar, aktiviteter och incheckningar. */
internal const val LEGACY_SOMATIC = "legacySomatic"

/**
 * `symptoms: [{optionId, score, customText}]` (DAT-6), i lagrad ordning; element som inte är objekt hoppas över. `score`
 * lagrat som `null` = utan poäng (från 3.x); saknad eller trasig poäng läses som 0, som förut.
 */
internal fun Doc.symptoms(): List<SymptomScore> = docs(SYMPTOMS).map {
    SymptomScore(optionId = it.string("optionId"), score = it.intKeepingNull("score"), customText = it.stringOrNull("customText"))
}

internal fun List<SymptomScore>.encodeSymptoms(): List<Doc> = map { mapOf("optionId" to it.optionId, "score" to it.score, "customText" to it.customText) }

/** Känslorna på en måendelogg och en aktivitet (DAT-14). */
internal const val FEELINGS = "feelings"

/**
 * `feelings: ["core", "core.nuance", "core.nuance.detail"]` (DAT-14), i lagrad ordning ([Feeling.parse]). Element som inte
 * är text, är tomma eller har fler än tre delar hoppas över; tokens läses som de är, så ett okänt med giltig form (från en
 * nyare app) bevaras och skrivs tillbaka oförändrat. Saknat fält = tom lista.
 */
internal fun Doc.feelings(): List<Feeling> = stringList(FEELINGS).mapNotNull(Feeling::parse)

internal fun List<Feeling>.encodeFeelings(): List<String> = map { it.token }

/** Styrka och kommentar per vald känsla (SCR-9, SCR-10, DAT-14, DAT-15) – kartor från token till värde bredvid [FEELINGS]. */
internal const val FEELING_STRENGTHS = "feelingStrengths"
internal const val FEELING_NOTES = "feelingNotes"

/**
 * En karta per vald känsla (`{"calm.grateful": …}`, [FEELING_STRENGTHS], [FEELING_NOTES]): en nyckel som inte är en vald
 * känsla i [feelings] (föräldralös) och ett värde som [value] inte godtar hoppas över; ett okänt värde som är text bevaras
 * (nyare app). Saknat fält = tom karta. Samma läsning för båda fälten (regel 4).
 */
internal fun Doc.feelingValues(key: String, feelings: List<Feeling>, value: (String) -> String? = { it }): Map<String, String> {
    val tokens = feelings.mapTo(HashSet()) { it.token }
    return asDoc(this[key]).mapNotNull { (token, raw) -> (raw as? String)?.let(value)?.takeIf { token in tokens }?.let { token to it } }.toMap()
}

/** Kartan som den skrivs: bara valda känslor, i valens ordning, värdena genom [value] (`null` = skrivs inte). */
internal fun Map<String, String>.encodeFeelingValues(feelings: List<Feeling>, value: (String) -> String? = { it }): Map<String, String> = feelings.mapNotNull { f -> this[f.token]?.let(value)?.let { f.token to it } }.toMap()

/**
 * En kommentar lagras trimmad och aldrig tom (SCR-10), utan enhetsavgränsaren U+001F – rules prövar kommentarerna
 * hopfogade med den (isFeelingNotes), så den får inte finnas i en kommentar.
 */
private fun note(text: String): String? = text.replace(NOTE_SEPARATOR, "").trim().ifEmpty { null }

private const val NOTE_SEPARATOR = "\u001f"

/** `feelings`, `feelingStrengths` och `feelingNotes` som de skrivs – en gång för måendeloggen och aktiviteten. */
internal fun HasFeelings<*>.encodeFeelingFields(): Doc = mapOf(
    FEELINGS to feelings.encodeFeelings(),
    FEELING_STRENGTHS to feelingStrengths.encodeFeelingValues(feelings),
    FEELING_NOTES to feelingNotes.encodeFeelingValues(feelings, ::note)
)

/** [post] med fälten ovan lästa ur [map]. */
internal fun <T : HasFeelings<T>> Doc.decodeFeelingFields(post: T): T {
    val feelings = feelings()
    return post.copyFeelings(feelings, feelingValues(FEELING_STRENGTHS, feelings), feelingValues(FEELING_NOTES, feelings, ::note))
}

object OptionCodec : DocCodec<Option> {
    const val NAME = "name"
    const val KIND = "kind"
    const val SORT_ORDER = "sortOrder"
    const val FAVORITE = "favorite"
    const val ARCHIVED = "archived"

    override fun encode(value: Option): Doc = mapOf(
        KIND to value.kind.encodeWire(),
        NAME to value.name,
        FAVORITE to value.favorite,
        SORT_ORDER to value.sortOrder,
        ARCHIVED to value.archived
    )

    override fun decode(id: String, map: Doc) = Option(
        id = id,
        kind = map.wire(KIND, OptionKind.ACTIVITY),
        name = map.string(NAME),
        favorite = map.bool(FAVORITE),
        sortOrder = map.int(SORT_ORDER),
        archived = map.bool(ARCHIVED)
    )
}

object ScreeningCodec : DatedCodec<Screening> {
    /** Loggens dag (`yyyy-MM-dd`, sorterbar som text) – avgränsar lyssningen på en dag eller period. */
    const val DATE = se.partee71.dagboken.core.schema.DATE

    override val dateField: String get() = DATE

    override fun encode(value: Screening): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "occasion" to value.occasion.encodeWire(),
        "customText" to value.customText,
        "energy" to value.energy,
        "stress" to value.stress,
        SYMPTOMS to value.symptoms.encodeSymptoms(),
        LEGACY_SOMATIC to value.legacySomatic
    ) + value.encodeFeelingFields()

    override fun decode(id: String, map: Doc) = Screening(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        occasion = map.wireOrNull<Occasion>("occasion"),
        customText = map.stringOrNull("customText"),
        energy = map.int("energy"),
        stress = map.int("stress"),
        symptoms = map.symptoms(),
        legacySomatic = map.intOrNull(LEGACY_SOMATIC),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE)
    ).let(map::decodeFeelingFields)
}

object ActivityCodec : DatedCodec<Activity> {
    /** Aktivitetens dag (`yyyy-MM-dd`) – avgränsar Dagbokens lyssning på ett år (HIST-8). */
    const val DATE = se.partee71.dagboken.core.schema.DATE

    override val dateField: String get() = DATE

    override fun encode(value: Activity): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "optionId" to value.optionId,
        "customText" to value.customText,
        "energy" to value.energy,
        "stress" to value.stress,
        SYMPTOMS to value.symptoms.encodeSymptoms(),
        LEGACY_SOMATIC to value.legacySomatic,
        "recovering" to value.recovering,
        "drain" to value.drain,
        "minutes" to value.minutes
    ) + value.encodeFeelingFields()

    override fun decode(id: String, map: Doc) = Activity(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        optionId = map.string("optionId"),
        customText = map.stringOrNull("customText"),
        energy = map.int("energy"),
        stress = map.int("stress"),
        symptoms = map.symptoms(),
        legacySomatic = map.intOrNull(LEGACY_SOMATIC),
        recovering = map.bool("recovering"),
        drain = map.bool("drain"),
        minutes = map.intOrNull("minutes"),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE)
    ).let(map::decodeFeelingFields)
}

object EventCodec : DatedCodec<Event> {
    /** Händelsens dag (`yyyy-MM-dd`) – avgränsar Dagbokens lyssning på ett år (HIST-8). */
    const val DATE = se.partee71.dagboken.core.schema.DATE

    override val dateField: String get() = DATE

    override fun encode(value: Event): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "optionId" to value.optionId,
        "severity" to value.severity,
        "durationMinutes" to value.durationMinutes,
        "triggers" to value.triggers,
        "actions" to value.actions
    )

    override fun decode(id: String, map: Doc) = Event(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        optionId = map.string("optionId"),
        severity = map.int("severity"),
        durationMinutes = map.int("durationMinutes"),
        triggers = map.stringOrNull("triggers"),
        actions = map.stringOrNull("actions"),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE)
    )
}

object IllnessEpisodeCodec : DocCodec<IllnessEpisode> {
    override fun encode(value: IllnessEpisode): Doc = mapOf(
        "type" to value.type,
        "start" to value.start.encodeDate(),
        "end" to value.end.encodeDate(),
        CREATED_AT to value.createdAt,
        NOTE to value.note
    )

    override fun decode(id: String, map: Doc) = IllnessEpisode(
        id = id,
        type = map.string("type"),
        start = map.localDate("start"),
        end = map.localDate("end"),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE)
    )
}

/** Incheckningen; episoden är sökvägen och lagras inte som fält. */
object CheckinCodec : DocCodec<Checkin> {
    override fun encode(value: Checkin): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "severity" to value.severity,
        SYMPTOMS to value.symptoms.encodeSymptoms(),
        LEGACY_SOMATIC to value.legacySomatic
    )

    override fun decode(id: String, map: Doc) = Checkin(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        severity = map.int("severity"),
        symptoms = map.symptoms(),
        legacySomatic = map.intOrNull(LEGACY_SOMATIC),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE)
    )
}
