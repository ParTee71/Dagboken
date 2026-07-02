package se.partee71.dagboken.domain.model

data class Medicin(
    val id: String,
    val timestamp: String,
    val datum: String,
    val tid: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkt: String,        // "Morgon" | "Förmiddag" | ... | "Vid behov"
    val tagen: Boolean,
    val anteckning: String,
    val receptId: String? = null,
    val skipped: Boolean = false,
)

data class Recept(
    val id: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkter: List<String>,
    val upprepning: String,      // "dagligen" | "vardagar" | "helger" | "anpassad" | "intervall"
    val dagar: List<Int>,        // 0=Mon..6=Sun for "anpassad"
    val intervalDagar: Int = 2,
    val anteckning: String,
    val aktiv: Boolean,
    val skapad: String,          // YYYY-MM-DD
)

enum class Upprepning {
    DAGLIGEN, VARDAGAR, HELGER, ANPASSAD, INTERVALL;

    companion object {
        fun fromString(s: String): Upprepning = when (s.lowercase()) {
            "vardagar"                    -> VARDAGAR
            "helger"                      -> HELGER
            "anpassad", "specifika dagar" -> ANPASSAD
            "intervall", "var x:e dag"    -> INTERVALL
            else                          -> DAGLIGEN
        }
    }
}

data class Favorit(
    val id: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkt: String,
    val anteckning: String,
    val minTidMellan: Int,       // hours cooldown
    val dispenseringsTid: String = "",
    val maxDoserPerDag: Int = 0, // 0 = no limit
    val isFavorite: Boolean = false,
)

// TIDP sort order — mirrors TIDP_ORDER in src/utils/storage.js
val TIDP_ORDER = listOf("Morgon", "Förmiddag", "Lunch", "Eftermiddag", "Kväll", "Natt", "Vid behov")

// Default clock times — single source of truth; mirrors TIDP_DEFAULT_TIMES in keys.ts
val TIDP_DEFAULT_TIMES = mapOf(
    "Morgon"       to "07:00",
    "Förmiddag"    to "10:00",
    "Lunch"        to "12:00",
    "Eftermiddag"  to "15:00",
    "Kväll"        to "19:00",
    "Natt"         to "22:00",
    "Vid behov"    to "12:00",
)

fun tidpunktSortIndex(tidpunkt: String): Int =
    TIDP_ORDER.indexOf(tidpunkt).takeIf { it >= 0 } ?: TIDP_ORDER.size

/** Returns the clock hour for [tidpunkt], or null for "Vid behov" (no fixed time). */
fun tidpunktToHour(tidpunkt: String): Int? {
    if (tidpunkt == "Vid behov") return null
    return TIDP_DEFAULT_TIMES[tidpunkt]?.substringBefore(":")?.toIntOrNull()
}

/**
 * Historik-filtertyp för en medicinpost. Härleds från [Medicin.receptId] eftersom
 * favorit-snabbval och engångsdoser inte skiljs åt i datamodellen (båda receptId == null).
 */
fun medicinHistoryType(medicin: Medicin): String =
    if (medicin.receptId != null) "recept" else "vid_behov"
