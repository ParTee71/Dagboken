package se.partee71.dagboken.core.model

import kotlinx.datetime.LocalTime

/**
 * Dagens medicintidpunkter i fast ordning (DAT-1) – recept, vid behov-mediciner, doser och
 * medicinpåminnelser. [legacyName] är 3.x-namnet (`tidpunkt`); det ingår i receptdosernas
 * dokument-id ([DoseIds]) och används av konverteraren – det får aldrig innehålla understreck,
 * eftersom `syncDoses` känner igen okopplade receptdoser på id-mönstret. [defaultTime] är standardklockslaget
 * (REC-6, NOT-18).
 */
enum class Slot(override val wire: String, val legacyName: String, val defaultTime: LocalTime) : WireEnum {
    MORNING("morning", "Morgon", LocalTime(7, 0)),
    MIDMORNING("midmorning", "Förmiddag", LocalTime(10, 0)),
    LUNCH("lunch", "Lunch", LocalTime(12, 0)),
    AFTERNOON("afternoon", "Eftermiddag", LocalTime(15, 0)),
    EVENING("evening", "Kväll", LocalTime(19, 0)),
    NIGHT("night", "Natt", LocalTime(22, 0)),
    AS_NEEDED("asNeeded", "Vid behov", LocalTime(12, 0)),
    ;

    companion object {
        /** Tidpunkterna med klockslag – de som kan påminna (NOT-18); "Vid behov" påminner inte. */
        val SCHEDULED: List<Slot> = entries - AS_NEEDED

        /** 3.x-namnet → tidpunkten; okänt namn → `null`. */
        fun fromLegacyName(name: String): Slot? = entries.firstOrNull { it.legacyName == name }
    }
}
