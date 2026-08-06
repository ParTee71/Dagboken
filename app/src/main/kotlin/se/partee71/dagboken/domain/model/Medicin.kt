package se.partee71.dagboken.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val receptId: String? = null,
    val skipped: Boolean = false,
    val tagenTid: String? = null, // "HH:mm" — faktisk tagningstid, skild från schemalagda tid (MED-14)
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
    val aktiv: Boolean,
    val skapad: String,          // YYYY-MM-DD
    val startDatum: String = "", // YYYY-MM-DD; "" = ingen uttalad periodstart (recept från före REC-7)
    val slutDatum: String? = null, // YYYY-MM-DD; null = tills vidare
    val dosperioder: List<Dosperiod> = emptyList(),
)

/**
 * En tillfällig **doshöjning** inom ett recepts period (REC-9) — t.ex. en kortare kur
 * med dubbel dos. [dos] är det som läggs *till* grunddosen, inte den nya totalen, och
 * anges alltid i receptets egen enhet ([enhet] speglar därför receptets).
 * Persisteras som JSON i `recept.dosperioderJson`, samma mönster som tidpunkter/dagar.
 */
@Serializable
data class Dosperiod(
    val id: String,
    val startDatum: String,        // YYYY-MM-DD
    val slutDatum: String? = null, // null = till receptets slut
    val dos: String,               // höjningen, i receptets enhet
    val enhet: String,
)

private fun parseIsoDate(value: String?): LocalDate? =
    value?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

/**
 * Periodens startdatum för visning och intervallberäkning (REC-4/REC-7) — skapandedatumet
 * för recept som saknar ett uttalat startdatum.
 */
val Recept.periodStart: String get() = startDatum.ifBlank { skapad }

/**
 * True om [date] ligger inom receptets period (REC-7). Saknat slutdatum = tills vidare.
 * Ett recept utan uttalat [Recept.startDatum] har ingen bakre gräns — annars skulle
 * bakåtbläddring i Idag (HEM-14) sluta seeda doser för dagar före receptet skapades.
 */
fun Recept.coversDate(date: LocalDate): Boolean {
    val start = parseIsoDate(startDatum)
    if (start != null && date.isBefore(start)) return false
    val end = parseIsoDate(slutDatum) ?: return true
    return !date.isAfter(end)
}

/** True om receptets period redan har passerats sett från [today] (REC-8). */
fun Recept.hasExpiredOn(today: LocalDate): Boolean {
    val end = parseIsoDate(slutDatum) ?: return false
    return today.isAfter(end)
}

/**
 * Dosperioden som gäller för [date], eller null om grunddosen gäller (REC-9).
 * Formuläret hindrar överlappande dosperioder, men äldre eller importerad data kan
 * innehålla dem — då vinner den senast påbörjade, alltså den mest specifika (en
 * nedtrappning som lagts in ovanpå en längre period ska gälla, inte tvärtom).
 */
fun Recept.dosperiodFor(date: LocalDate): Dosperiod? =
    dosperioder
        .filter { p ->
            val start = parseIsoDate(p.startDatum) ?: return@filter false
            if (date.isBefore(start)) return@filter false
            val end = parseIsoDate(p.slutDatum) ?: return@filter true
            !date.isAfter(end)
        }
        .maxByOrNull { it.startDatum }

/**
 * Läser en dos som tal. Accepterar både punkt och komma som decimaltecken, eftersom
 * doser skrivs in för hand ("0,5", "1.5"). Null när dosen inte är ett tal ("1 tablett").
 */
fun parseDos(value: String?): Double? =
    value?.trim()?.replace(',', '.')?.toDoubleOrNull()

/** Formaterar en uträknad dos för visning: heltal utan decimaler, annars svenskt komma. */
fun formatDos(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else value.toString().trimEnd('0').trimEnd('.').replace('.', ',')

/**
 * Total dos och enhet som gäller för [date] (REC-9/REC-12) — grunddosen plus den
 * doshöjning som eventuellt gäller den dagen. Enheten är alltid receptets.
 * Går grunddosen eller höjningen inte att räkna med som tal används grunddosen oförändrad.
 */
fun Recept.dosFor(date: LocalDate): Pair<String, String> {
    val hojning = dosperiodFor(date) ?: return dos to enhet
    val bas = parseDos(dos) ?: return dos to enhet
    val extra = parseDos(hojning.dos) ?: return dos to enhet
    return formatDos(bas + extra) to enhet
}

/** Doshöjningen som gäller för [date], formaterad med tecken ("+2 mg"), eller null. */
fun Recept.hojningFor(date: LocalDate): String? {
    val hojning = dosperiodFor(date) ?: return null
    val extra = parseDos(hojning.dos) ?: return null
    return "+${formatDos(extra)} $enhet"
}

/** Sista dagen för [Dosperiod], begränsad av receptets egen period. */
fun Recept.dosperiodEnd(dosperiod: Dosperiod): LocalDate? =
    parseIsoDate(dosperiod.slutDatum) ?: parseIsoDate(slutDatum)

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
