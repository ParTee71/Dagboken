package se.partee71.dagboken.core.model

import kotlinx.datetime.LocalTime

/**
 * Dagens medicintidpunkter i fast ordning (DAT-1) – recept, vid behov-mediciner, doser och
 * medicinpåminnelser. [legacyName] är 3.x-namnet (`tidpunkt`); det ingår i receptdosernas
 * dokument-id ([DoseIds]), också i de doser som redan ligger i kontona – det får aldrig ändras eller innehålla understreck,
 * eftersom `syncDoses` känner igen okopplade receptdoser på id-mönstret. [defaultTime] är standardklockslaget
 * (REC-6) – förvalet tills tidpunktens klockslag ställts i påminnelserna; använd [ReminderSettings.timeOf] (NOT-18).
 */
enum class Slot(override val wire: String, val legacyName: String, val defaultTime: LocalTime) : WireEnum {
    MORNING("morning", "Morgon", LocalTime(7, 0)),
    MIDMORNING("midmorning", "Förmiddag", LocalTime(10, 0)),
    LUNCH("lunch", "Lunch", LocalTime(12, 0)),
    AFTERNOON("afternoon", "Eftermiddag", LocalTime(15, 0)),
    EVENING("evening", "Kväll", LocalTime(19, 0)),
    NIGHT("night", "Natt", LocalTime(22, 0)),
    AS_NEEDED("asNeeded", "Vid behov", LocalTime(12, 0))
    ;

    companion object {
        /** Tidpunkterna med klockslag – de som kan påminna (NOT-18); "Vid behov" påminner inte. */
        val SCHEDULED: List<Slot> = entries - AS_NEEDED
    }
}
