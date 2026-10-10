package se.partee71.dagboken.core.model

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * `users/{uid}/prescriptions/{id}` – ett recept med schema (REC-1…REC-13). Id:t är 3.x-receptets
 * id (DAT-13). Ersätter 3.x `recept` med `dosperioderJson`.
 */
data class Prescription(
    override val id: String,
    /** Medicinens namn. */
    val name: String = "",
    /** Grunddosen som text, som 3.x (`"0,5"`, `"1 tablett"`); räknas som tal där den går (REC-9). */
    val dose: String = "",
    /** Enheten (`mg`, `st` …). */
    val unit: String = "",
    /** Dagens tidpunkter, i den ordning de valts. */
    val slots: List<Slot> = emptyList(),
    /** Upprepningen (REC-2…REC-4). */
    val schedule: Schedule = Schedule.Unknown(),
    /** Perioden (REC-7). */
    val period: Period = Period(),
    /** Tillfälliga doshöjningar inom perioden (REC-9). */
    val boosts: List<Boost> = emptyList(),
    /** Aktivt eller avslutat (REC-5, REC-8). */
    val active: Boolean = true,
    /** När receptet skapades; från 3.x `skapad` (datum) som midnatt Europe/Stockholm. */
    val createdAt: Instant? = null,
    /** Anteckningen (DAT-7). */
    val note: String? = null,
    /** Styrkan som text, som i Läkemedelsverkets lista (`"500 mg"`, `"0,5 mg/dos"`); tom = ej angiven (REC-1). Sist bland fälten, så att positionella anrop står kvar. */
    val strength: String = "",
    /** Läkemedelsformen; `null` = ej angiven (3.x saknade fältet) eller okänd ([unknownForm]). */
    val form: MedicineForm? = null,
    /**
     * Tidpunkter i `slots` som appen inte känner till (t.ex. från en nyare app), som råa lagrade
     * värden: ger inga doser och skrivs tillbaka oförändrade efter de kända – som [Schedule.Unknown].
     * Inget eget fält i dokumentet utan en del av `slots`; därför `@Transient` (codec-testernas
     * fältlista räknar bara egna fält).
     */
    @Transient val unknownSlots: List<String> = emptyList(),
    /**
     * En form i `form` som appen inte känner till (från en nyare app), som det råa lagrade värdet: skrivs
     * tillbaka oförändrad så länge [form] är `null`. Bärs i fältet `form`, därför `@Transient` som [unknownSlots].
     */
    @Transient val unknownForm: String? = null,
) : Identified {
    /** "Levaxin 50 mikrogram" – namn och styrka, tomma delar utelämnade. */
    val displayName: String get() = medicineTitle(name, strength)

    /**
     * Tidpunkterna kan inte visas eller ändras i formuläret: någon är okänd ([unknownSlots]) eller
     * "Vid behov" (som ett recept aldrig ger doser för, äldre eller importerad data). Formuläret visar
     * dem då som "kan inte visas" och skriver aldrig `slots`.
     */
    val hasUnknownSlots: Boolean get() = unknownSlots.isNotEmpty() || Slot.AS_NEEDED in slots
}

/** Receptets upprepning. */
sealed interface Schedule {
    /**
     * Ett känt mönster. [days] och [intervalDays] bevaras oavsett [repeat], som i 3.x – ett byte
     * av upprepning och tillbaka tappar dem inte.
     */
    data class Repeating(
        val repeat: Repeat = Repeat.DAILY,
        /** Veckodagarna för [Repeat.CUSTOM]. */
        val days: Set<DayOfWeek> = emptySet(),
        /** Var n:e dag för [Repeat.INTERVAL], räknat från periodens start eller skapandedagen (REC-4). */
        val intervalDays: Int = 2,
    ) : Schedule

    /**
     * Saknad eller okänd upprepning, t.ex. från en nyare app: visas som "kan inte visas", ger inga
     * doser, och [raw] (fältets råa värde) skrivs tillbaka oförändrat.
     */
    data class Unknown(val raw: Any? = null) : Schedule
}

enum class Repeat(override val wire: String) : WireEnum {
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKENDS("weekends"),
    CUSTOM("custom"),
    INTERVAL("interval"),
}

/**
 * Receptets period (REC-7). [start] `null` = ingen uttalad start (3.x-recept från före
 * periodstödet): ingen bakre gräns, och intervallet räknas från skapandedagen. [end] `null` =
 * tills vidare.
 */
data class Period(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)

/**
 * En doshöjning (REC-9, 3.x `Dosperiod`): [dose] läggs till grunddosen. [unit] bevaras från 3.x
 * även om den alltid speglar receptets enhet. [end] `null` = till periodens slut.
 */
data class Boost(
    override val id: String = "",
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val dose: String = "",
    val unit: String = "",
) : Identified
