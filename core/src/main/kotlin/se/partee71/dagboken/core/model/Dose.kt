package se.partee71.dagboken.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * `users/{uid}/doses/{id}` – en dos: planerad ur ett recept, tagen eller överhoppad (DAT-8,
 * MED-1…MED-16). Id:t är 3.x-postens id; receptgenererade doser har [DoseIds.prescribed].
 * Ersätter 3.x `mediciner`.
 */
data class Dose(
    override val id: String,
    /** Dagen dosen hör till. */
    val date: LocalDate? = null,
    val slot: Slot = Slot.AS_NEEDED,
    /** Namn, styrka, dos och enhet kopierade från receptet eller medicinen när dosen skapades. */
    val name: String = "",
    val dose: String = "",
    val unit: String = "",
    val status: DoseStatus = DoseStatus.PLANNED,
    /** Schemalagt klockslag (3.x `tid`). */
    val plannedTime: LocalTime? = null,
    /** När dosen togs (MED-14); 3.x `tagenTid` (klockslag) på dosens dag, Europe/Stockholm. */
    val takenAt: Instant? = null,
    /** Receptet dosen genererades från. */
    val prescriptionId: String? = null,
    /** Vid behov-medicinen dosen loggades från (fanns inte i 3.x). */
    val prnId: String? = null,
    /** När posten skapades; 3.x `timestamp`. */
    val createdAt: Instant? = null,
    /** Anteckningen (DAT-7). */
    val note: String? = null,
    /** Styrkan (`"500 mg"`), kopierad med namnet; tom = ej angiven, som alla doser från 3.x. */
    val strength: String = "",
) : Identified {
    /** "Alvedon 500 mg" – namn och styrka, tomma delar utelämnade. */
    val displayName: String get() = medicineTitle(name, strength)
}

/** Dosens tillstånd – ersätter 3.x `tagen` och `skipped`. */
enum class DoseStatus(override val wire: String) : WireEnum {
    PLANNED("planned"),
    TAKEN("taken"),
    SKIPPED("skipped"),
}

/** Dokument-id för doser (DAT-8). */
object DoseIds {
    /**
     * Receptgenererad dos: `recept_{prescriptionId}_{date}_{tidpunkt}` med tidpunktens 3.x-namn –
     * exakt 3.x-schemat (MED-4), så att genereringen i 4.0 träffar redan migrerade doser och en
     * upprepad import aldrig dubblerar. Bara schemalagda tidpunkter ([Slot.SCHEDULED]): ett recept
     * genererar aldrig vid behov-doser (3.x erbjöd inte "Vid behov" på recept), och en vid
     * behov-dos får ett vanligt id – "Vid behov" har dessutom ett mellanslag som dokument-id inte tål.
     */
    fun prescribed(prescriptionId: String, date: LocalDate, slot: Slot): String {
        require(slot != Slot.AS_NEEDED) { "Receptdoser har en schemalagd tidpunkt, inte ${slot.legacyName}" }
        return "recept_${prescriptionId}_${date}_${slot.legacyName}"
    }
}
