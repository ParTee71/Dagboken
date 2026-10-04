package se.partee71.dagboken.core.model

/**
 * `users/{uid}/options/{id}` – ett alternativ i någon av listorna aktivitet, symptom eller
 * händelsetyp (DAT-9). Poster refererar alternativet med `optionId`, så ett namnbyte syns i
 * historiken (SET-11). Ett använt alternativ arkiveras i stället för att raderas.
 */
data class Option(
    override val id: String,
    /** Vilken lista alternativet hör till. */
    val kind: OptionKind = OptionKind.ACTIVITY,
    /** Namnet som visas. */
    val name: String = "",
    /** Stjärnmärkt: visas som en-tryck-chip (SET-5, SET-9). */
    val favorite: Boolean = false,
    /** Plats i listan (3.x: listans ordning). */
    override val sortOrder: Int = 0,
    /** Dold i formulären men kvar för poster som refererar det. */
    override val archived: Boolean = false,
) : Identified, Sortable, Archivable

enum class OptionKind(override val wire: String) : WireEnum {
    ACTIVITY("activity"),
    SYMPTOM("symptom"),
    EVENT("event"),
}
