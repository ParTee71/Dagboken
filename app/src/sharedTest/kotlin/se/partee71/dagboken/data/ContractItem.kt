package se.partee71.dagboken.data

import kotlin.time.Instant
import se.partee71.dagboken.core.model.Archivable
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.Sortable
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.ValueCodec
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.core.schema.bool
import se.partee71.dagboken.core.schema.instant
import se.partee71.dagboken.core.schema.int
import se.partee71.dagboken.core.schema.nested
import se.partee71.dagboken.core.schema.string
import se.partee71.dagboken.core.schema.stringOrNull

/**
 * Provmodellen som `CollectionContract` körs mot tills appens modeller och codecs finns
 * (etapp 2) – då byts den mot riktiga samlingar, som i ReseApoteket. Formad som `options`
 * (ARKITEKTUR.md → Datamodell) och lagrad där, med ett nästlat schema som
 * `prescriptions.schedule`, så att kontraktet täcker samma fall: valfritt fält, nästlad map med
 * okänd variant, `createdAt`/`updatedAt`, arkivering och sortering.
 */
data class ContractItem(
    override val id: String,
    val name: String = "",
    val kind: String = "activity",
    val favorite: Boolean = false,
    val note: String? = null,
    val schedule: ContractSchedule = ContractSchedule.Daily(),
    override val sortOrder: Int = 0,
    override val archived: Boolean = false,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) : Identified, Sortable, Archivable

/** Ett nästlat värde med varianter; en variant från en nyare app läses som [Unknown] och skrivs tillbaka orörd. */
sealed interface ContractSchedule {
    data class Daily(val times: Int = 1) : ContractSchedule

    data class Interval(val days: Int = 2) : ContractSchedule

    data class Unknown(val raw: Any?) : ContractSchedule
}

object ContractScheduleCodec : ValueCodec<ContractSchedule> {
    override fun encode(value: ContractSchedule): Any? = when (value) {
        is ContractSchedule.Daily -> fields("DAILY", times = value.times)
        is ContractSchedule.Interval -> fields("INTERVAL", days = value.days)
        is ContractSchedule.Unknown -> value.raw
    }

    override fun decode(raw: Any?): ContractSchedule {
        if (raw == null) return ContractSchedule.Daily()
        val map = asDoc(raw)
        return when (map["type"]) {
            "DAILY" -> ContractSchedule.Daily(map.int("times", 1))
            "INTERVAL" -> ContractSchedule.Interval(map.int("days", 2))
            else -> ContractSchedule.Unknown(raw)
        }
    }

    /** Alla varianter skriver samma nycklar, så att inga blir kvar från en tidigare variant. */
    private fun fields(type: String, times: Int? = null, days: Int? = null): Doc = mapOf("type" to type, "times" to times, "days" to days)
}

object ContractItemCodec : DocCodec<ContractItem> {
    const val NAME = "name"
    const val KIND = "kind"
    const val SCHEDULE = "schedule"

    override fun encode(value: ContractItem): Map<String, Any?> = mapOf(
        NAME to value.name,
        KIND to value.kind,
        "favorite" to value.favorite,
        "note" to value.note,
        SCHEDULE to ContractScheduleCodec.encode(value.schedule),
        "sortOrder" to value.sortOrder,
        "archived" to value.archived,
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
    )

    override fun decode(id: String, map: Map<String, Any?>) = ContractItem(
        id = id,
        name = map.string(NAME),
        kind = map.string(KIND, "activity"),
        favorite = map.bool("favorite"),
        note = map.stringOrNull("note"),
        schedule = map.nested(SCHEDULE, ContractScheduleCodec),
        sortOrder = map.int("sortOrder"),
        archived = map.bool("archived"),
        createdAt = map.instant("createdAt"),
        updatedAt = map.instant("updatedAt"),
    )
}
