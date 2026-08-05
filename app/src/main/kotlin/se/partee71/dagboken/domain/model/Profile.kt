package se.partee71.dagboken.domain.model

import java.time.LocalDate

/**
 * Kön, för de åldersnormer sömnkvaliteten jämförs mot (HLS-11). Djupsömnen sjunker
 * brantare med åldern hos män än hos kvinnor, så normen behöver veta vilken kurva som
 * gäller. [EJ_ANGIVET] använder ett mellanvärde och är standard — appen ska fungera
 * utan att användaren uppger något.
 */
enum class Sex {
    MAN,
    KVINNA,
    EJ_ANGIVET,
    ;

    /** Stabil nyckel för DataStore och backup — enum-namnen får inte läcka till lagringen. */
    val storageKey: String
        get() = when (this) {
            MAN -> "man"
            KVINNA -> "kvinna"
            EJ_ANGIVET -> "ej_angivet"
        }

    companion object {
        fun fromStorageKey(key: String?): Sex =
            entries.firstOrNull { it.storageKey == key } ?: EJ_ANGIVET
    }
}

/** Rimliga födelseår — utanför spannet är värdet en felinmatning, inte en ålder. */
val BIRTH_YEAR_RANGE: IntRange get() = (LocalDate.now().year - 120)..LocalDate.now().year

/** Ålder i hela år från [birthYear], räknat mot [today]. Null om året saknas eller är orimligt. */
fun ageFromBirthYear(birthYear: Int?, today: LocalDate = LocalDate.now()): Int? {
    if (birthYear == null || birthYear !in (today.year - 120)..today.year) return null
    return today.year - birthYear
}
