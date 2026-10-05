package se.partee71.dagboken.core.schema

import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
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
    note: String?,
): Doc = mapOf(DATE to date.encodeDate(), TIME to time.encodeTime(), CREATED_AT to createdAt, NOTE to note)

/** `symptoms: [{optionId, score, customText}]` (DAT-6), i lagrad ordning; element som inte är objekt hoppas över. */
internal fun Doc.symptoms(): List<SymptomScore> = docs(SYMPTOMS).map {
    SymptomScore(optionId = it.string("optionId"), score = it.int("score"), customText = it.stringOrNull("customText"))
}

internal fun List<SymptomScore>.encodeSymptoms(): List<Doc> =
    map { mapOf("optionId" to it.optionId, "score" to it.score, "customText" to it.customText) }

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
        ARCHIVED to value.archived,
    )

    override fun decode(id: String, map: Doc) = Option(
        id = id,
        kind = map.wire(KIND, OptionKind.ACTIVITY),
        name = map.string(NAME),
        favorite = map.bool(FAVORITE),
        sortOrder = map.int(SORT_ORDER),
        archived = map.bool(ARCHIVED),
    )
}

object ScreeningCodec : DocCodec<Screening> {
    override fun encode(value: Screening): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "occasion" to value.occasion.encodeWire(),
        "customText" to value.customText,
        "energy" to value.energy,
        "stress" to value.stress,
        SYMPTOMS to value.symptoms.encodeSymptoms(),
    )

    override fun decode(id: String, map: Doc) = Screening(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        occasion = map.wireOrNull<Occasion>("occasion"),
        customText = map.stringOrNull("customText"),
        energy = map.int("energy"),
        stress = map.int("stress"),
        symptoms = map.symptoms(),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE),
    )
}

object ActivityCodec : DocCodec<Activity> {
    override fun encode(value: Activity): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "optionId" to value.optionId,
        "customText" to value.customText,
        "energy" to value.energy,
        "stress" to value.stress,
        SYMPTOMS to value.symptoms.encodeSymptoms(),
        "recovering" to value.recovering,
        "drain" to value.drain,
        "minutes" to value.minutes,
    )

    override fun decode(id: String, map: Doc) = Activity(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        optionId = map.string("optionId"),
        customText = map.stringOrNull("customText"),
        energy = map.int("energy"),
        stress = map.int("stress"),
        symptoms = map.symptoms(),
        recovering = map.bool("recovering"),
        drain = map.bool("drain"),
        minutes = map.intOrNull("minutes"),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE),
    )
}

object EventCodec : DocCodec<Event> {
    override fun encode(value: Event): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "optionId" to value.optionId,
        "severity" to value.severity,
        "durationMinutes" to value.durationMinutes,
        "triggers" to value.triggers,
        "actions" to value.actions,
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
        note = map.stringOrNull(NOTE),
    )
}

object IllnessEpisodeCodec : DocCodec<IllnessEpisode> {
    override fun encode(value: IllnessEpisode): Doc = mapOf(
        "type" to value.type,
        "start" to value.start.encodeDate(),
        "end" to value.end.encodeDate(),
        CREATED_AT to value.createdAt,
        NOTE to value.note,
    )

    override fun decode(id: String, map: Doc) = IllnessEpisode(
        id = id,
        type = map.string("type"),
        start = map.localDate("start"),
        end = map.localDate("end"),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE),
    )
}

/** Incheckningen; episoden är sökvägen och lagras inte som fält. */
object CheckinCodec : DocCodec<Checkin> {
    override fun encode(value: Checkin): Doc = entryFields(value.date, value.time, value.createdAt, value.note) + mapOf(
        "severity" to value.severity,
        SYMPTOMS to value.symptoms.encodeSymptoms(),
    )

    override fun decode(id: String, map: Doc) = Checkin(
        id = id,
        date = map.localDate(DATE),
        time = map.localTime(TIME),
        severity = map.int("severity"),
        symptoms = map.symptoms(),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE),
    )
}
