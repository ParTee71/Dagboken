package se.partee71.dagboken.core.schema

import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.MedicineForm
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot

// Codecs för recept, vid behov-mediciner och doser (ARKITEKTUR.md → Datamodell).

/** Styrkan (REC-1, FAV-1, dosens kopia) – samma fältnamn i alla tre samlingarna. */
internal const val STRENGTH = "strength"

/** Läkemedelsformen ([MedicineForm]); ett okänt värde skrivs tillbaka oförändrat. */
internal const val FORM = "form"

object PrescriptionCodec : DocCodec<Prescription> {
    const val ACTIVE = "active"
    const val SLOTS = "slots"
    const val PERIOD = "period"
    const val BOOSTS = "boosts"

    override fun encode(value: Prescription): Doc = mapOf(
        "name" to value.name,
        STRENGTH to value.strength,
        FORM to value.form.encodeWire(value.unknownForm),
        "dose" to value.dose,
        "unit" to value.unit,
        // Okända tidpunkter (från en nyare app) skrivs tillbaka efter de kända.
        SLOTS to value.slots.encodeWires() + value.unknownSlots,
        "schedule" to ScheduleCodec.encode(value.schedule),
        PERIOD to PeriodCodec.encode(value.period),
        BOOSTS to value.boosts.map(BoostCodec::encode),
        ACTIVE to value.active,
        CREATED_AT to value.createdAt,
        NOTE to value.note,
    )

    override fun decode(id: String, map: Doc) = Prescription(
        id = id,
        name = map.string("name"),
        strength = map.string(STRENGTH),
        form = map.wireOrNull<MedicineForm>(FORM),
        dose = map.string("dose"),
        unit = map.string("unit"),
        slots = map.wireList<Slot>(SLOTS),
        schedule = map.nested("schedule", ScheduleCodec),
        period = map.nested(PERIOD, PeriodCodec),
        boosts = map.docs(BOOSTS).map(BoostCodec::decode),
        active = map.bool(ACTIVE, default = true),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE),
        unknownSlots = map.unknownWires<Slot>(SLOTS),
        unknownForm = map.unknownWire<MedicineForm>(FORM),
    )
}

/**
 * `schedule` i ett recept: `{repeat, days, intervalDays}`. Alla tre skrivs alltid och bevaras
 * oavsett upprepning, som i 3.x. Saknad eller okänd upprepning blir [Schedule.Unknown] och
 * skrivs tillbaka exakt som den lästes.
 */
object ScheduleCodec : ValueCodec<Schedule> {
    override fun encode(value: Schedule): Any? = when (value) {
        is Schedule.Unknown -> value.raw
        is Schedule.Repeating -> mapOf(
            "repeat" to value.repeat.encodeWire(),
            "days" to value.days.encodeWeekdays(),
            "intervalDays" to value.intervalDays,
        )
    }

    override fun decode(raw: Any?): Schedule {
        val map = asDoc(raw)
        val repeat = map.wireOrNull<Repeat>("repeat") ?: return Schedule.Unknown(raw)
        return Schedule.Repeating(repeat, map.weekdays("days"), map.int("intervalDays", 2))
    }
}

/** `period` i ett recept: `{start, end}`, båda alltid skrivna. */
object PeriodCodec : ValueCodec<Period> {
    override fun encode(value: Period): Any = mapOf("start" to value.start.encodeDate(), "end" to value.end.encodeDate())

    override fun decode(raw: Any?): Period = asDoc(raw).let { Period(it.localDate("start"), it.localDate("end")) }
}

/** Ett element i `boosts`: `{id, start, end, dose, unit}`. */
object BoostCodec {
    fun encode(value: Boost): Doc = mapOf(
        "id" to value.id,
        "start" to value.start.encodeDate(),
        "end" to value.end.encodeDate(),
        "dose" to value.dose,
        "unit" to value.unit,
    )

    fun decode(map: Doc) = Boost(
        id = map.string("id"),
        start = map.localDate("start"),
        end = map.localDate("end"),
        dose = map.string("dose"),
        unit = map.string("unit"),
    )
}

object PrnMedicineCodec : DocCodec<PrnMedicine> {
    const val FAVORITE = "favorite"

    override fun encode(value: PrnMedicine): Doc = mapOf(
        "name" to value.name,
        STRENGTH to value.strength,
        FORM to value.form.encodeWire(value.unknownForm),
        "dose" to value.dose,
        "unit" to value.unit,
        "slot" to value.slot.encodeWire(),
        "minHoursBetween" to value.minHoursBetween,
        "dispensingTime" to value.dispensingTime,
        "maxPerDay" to value.maxPerDay,
        FAVORITE to value.favorite,
        NOTE to value.note,
    )

    override fun decode(id: String, map: Doc) = PrnMedicine(
        id = id,
        name = map.string("name"),
        strength = map.string(STRENGTH),
        form = map.wireOrNull<MedicineForm>(FORM),
        dose = map.string("dose"),
        unit = map.string("unit"),
        slot = map.wire("slot", Slot.AS_NEEDED),
        minHoursBetween = map.int("minHoursBetween", 4),
        dispensingTime = map.stringOrNull("dispensingTime"),
        maxPerDay = map.int("maxPerDay"),
        favorite = map.bool(FAVORITE),
        note = map.stringOrNull(NOTE),
        unknownForm = map.unknownWire<MedicineForm>(FORM),
    )
}

object DoseCodec : DocCodec<Dose> {
    /** Dosens dag (`yyyy-MM-dd`, sorterbar som text) – avgränsar läsningen av dagens och senare doser. */
    const val DATE = se.partee71.dagboken.core.schema.DATE

    // Fälten som avbockningen skriver var för sig (MED-2, NOT-10).
    const val STATUS = "status"

    // Fälten som följer receptet när det ändras (REC-10, REC-12).
    const val NAME = "name"
    const val STRENGTH = se.partee71.dagboken.core.schema.STRENGTH
    const val DOSE = "dose"
    const val UNIT = "unit"
    const val TAKEN_AT = "takenAt"

    // Fälten som är givna av en receptdos id (`recept_{prescriptionId}_{date}_{tidpunkt}`, NOT-10).
    const val SLOT = "slot"
    const val PRESCRIPTION_ID = "prescriptionId"

    /** Anteckningen (DAT-7) – skrivs aldrig blint över av "Markera tagen" utan nät (NOT-10). */
    const val NOTE = se.partee71.dagboken.core.schema.NOTE

    override fun encode(value: Dose): Doc = mapOf(
        DATE to value.date.encodeDate(),
        SLOT to value.slot.encodeWire(),
        NAME to value.name,
        STRENGTH to value.strength,
        DOSE to value.dose,
        UNIT to value.unit,
        STATUS to value.status.encodeWire(),
        "plannedTime" to value.plannedTime.encodeTime(),
        TAKEN_AT to value.takenAt,
        PRESCRIPTION_ID to value.prescriptionId,
        "prnId" to value.prnId,
        CREATED_AT to value.createdAt,
        NOTE to value.note,
    )

    override fun decode(id: String, map: Doc) = Dose(
        id = id,
        date = map.localDate(DATE),
        slot = map.wire(SLOT, Slot.AS_NEEDED),
        name = map.string("name"),
        strength = map.string(STRENGTH),
        dose = map.string("dose"),
        unit = map.string("unit"),
        status = map.wire(STATUS, DoseStatus.PLANNED),
        plannedTime = map.localTime("plannedTime"),
        takenAt = map.instant(TAKEN_AT),
        prescriptionId = map.stringOrNull(PRESCRIPTION_ID),
        prnId = map.stringOrNull("prnId"),
        createdAt = map.instant(CREATED_AT),
        note = map.stringOrNull(NOTE),
    )
}
