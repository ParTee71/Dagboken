package se.partee71.dagboken.core.model

/**
 * `users/{uid}/prnMedicines/{id}` – en vid behov-medicin (FAV-1…FAV-11, MEDF-3). Id:t är
 * 3.x-favoritens id (DAT-13). Ersätter 3.x `favoriter`.
 */
data class PrnMedicine(
    override val id: String,
    val name: String = "",
    /** Dosen som text, som 3.x. */
    val dose: String = "",
    val unit: String = "",
    /** Tidpunkten dosen loggas på; i praktiken "Vid behov". */
    val slot: Slot = Slot.AS_NEEDED,
    /** Kylperiod i timmar (FAV-4); 0 = ingen. */
    val minHoursBetween: Int = 4,
    /** Dispenseringstid (FAV-7) som fritext, som 3.x `dispenseringsTid`; visas inte i UI:t men bevaras. */
    val dispensingTime: String? = null,
    /** Högst antal doser per dag (FAV-5); 0 = obegränsat. */
    val maxPerDay: Int = 0,
    /** Stjärnmärkt: snabbval på Idag (FAV-2). */
    val favorite: Boolean = false,
    /** Anteckningen (DAT-7). */
    val note: String? = null,
) : Identified
